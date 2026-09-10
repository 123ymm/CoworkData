package com.huawei.coworkdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.coworkdata.entity.SessionEntity;
import com.huawei.coworkdata.entity.UserProfileEntity;
import com.huawei.coworkdata.mapper.SessionMapper;
import com.huawei.coworkdata.mapper.UserProfileMapper;
import com.huawei.coworkdata.service.IdentityMigrationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IdentityMigrationServiceImpl implements IdentityMigrationService {

    private static final Logger log = LoggerFactory.getLogger(IdentityMigrationServiceImpl.class);
    private static final Pattern UUID_RE = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final UserProfileMapper userProfileMapper;
    private final SessionMapper sessionMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${substrate.base-url:}")
    private String substrateBaseUrl;

    @Override
    @Transactional
    public Map<String, Object> migrateIdentity() {
        Map<String, Object> result = new HashMap<>();
        applyV4Ddl();
        result.put("ddlApplied", true);

        String base = substrateBaseUrl == null ? "" : substrateBaseUrl.trim().replaceAll("/+$", "");
        if (base.isEmpty()) {
            result.put("error", "substrate.base-url / SUBSTRATE_BASE_URL is required");
            result.put("mapped", 0);
            result.put("failed", 0);
            result.put("skippedUuid", 0);
            return result;
        }

        Set<String> candidates = new HashSet<>();
        for (UserProfileEntity p : userProfileMapper.selectList(null)) {
            if (p.getUserId() != null && !p.getUserId().trim().isEmpty()) {
                candidates.add(p.getUserId().trim());
            }
        }
        for (SessionEntity s : sessionMapper.selectList(
                new LambdaQueryWrapper<SessionEntity>().isNotNull(SessionEntity::getUserId))) {
            if (s.getUserId() != null && !s.getUserId().trim().isEmpty()) {
                candidates.add(s.getUserId().trim());
            }
        }

        int skippedUuid = 0;
        int mapped = 0;
        int failed = 0;
        List<String> failures = new ArrayList<>();
        Map<String, String> mapping = new HashMap<>();

        for (String id : candidates) {
            if (UUID_RE.matcher(id).matches()) {
                skippedUuid++;
                continue;
            }
            try {
                String token = fetchLocalToken(base, id);
                String sub = jwtSub(token);
                if (sub == null || sub.isEmpty()) {
                    failed++;
                    failures.add(id + ": empty sub");
                    continue;
                }
                mapping.put(id, sub);
                mapped++;
            } catch (Exception e) {
                failed++;
                failures.add(id + ": " + e.getMessage());
                log.warn("identity migrate failed for {}: {}", id, e.toString());
            }
        }

        for (Map.Entry<String, String> e : mapping.entrySet()) {
            String empNo = e.getKey();
            String surrogate = e.getValue();
            // 新口径：user_profile.PK=工号；sessions.user_id=工号，surrogate_id=JWT sub
            ensureEmpNoProfile(empNo);
            jdbcTemplate.update(
                    "UPDATE sessions SET surrogate_id = ? "
                            + "WHERE (user_id = ? OR surrogate_id IS NULL OR TRIM(COALESCE(surrogate_id,'')) = '') "
                            + "AND (user_id = ? OR user_id = ?)",
                    surrogate, empNo, empNo, surrogate);
            jdbcTemplate.update(
                    "UPDATE sessions SET user_id = ? WHERE user_id = ?",
                    empNo, surrogate);
        }

        jdbcTemplate.update(
                "UPDATE sessions SET source = 'local' WHERE source IS NULL OR TRIM(source) = ''");

        // 尝试从 config_json.template_id 回填 cowork_id，并重写 tenant_id
        List<SessionEntity> sessions = sessionMapper.selectList(null);
        int tenantRewritten = 0;
        for (SessionEntity s : sessions) {
            boolean dirty = false;
            if ((s.getCoworkId() == null || s.getCoworkId().trim().isEmpty())
                    && s.getConfigJson() != null) {
                String cid = extractTemplateId(s.getConfigJson());
                if (cid != null && !cid.isEmpty()) {
                    s.setCoworkId(cid);
                    dirty = true;
                }
            }
            if (s.getUserId() != null && !s.getUserId().isEmpty()
                    && s.getCoworkId() != null && !s.getCoworkId().isEmpty()) {
                String expected = s.getUserId() + ":" + s.getCoworkId();
                if (!expected.equals(s.getTenantId())) {
                    s.setTenantId(expected);
                    dirty = true;
                    tenantRewritten++;
                }
            }
            if (dirty) {
                sessionMapper.updateById(s);
            }
        }

        result.put("mapped", mapped);
        result.put("failed", failed);
        result.put("skippedUuid", skippedUuid);
        result.put("tenantRewritten", tenantRewritten);
        result.put("failures", failures);
        return result;
    }

    private void applyV4Ddl() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("db/migration/V4__surrogate_source_cowork.sql"));
        populator.setContinueOnError(true);
        populator.execute(dataSource);
    }

    private void ensureEmpNoProfile(String empNo) {
        if (empNo == null || empNo.trim().isEmpty()) {
            return;
        }
        UserProfileEntity existing = userProfileMapper.selectById(empNo);
        if (existing != null) {
            return;
        }
        UserProfileEntity neu = new UserProfileEntity();
        neu.setUserId(empNo.trim());
        neu.setUsername("");
        userProfileMapper.insert(neu);
    }

    private void remapUserProfile(String oldId, String newId) {
        // 保留旧方法签名以免反射调用；新口径不再把 PK 改成 surrogate
        ensureEmpNoProfile(oldId);
    }

    private String fetchLocalToken(String base, String username) throws Exception {
        URL url = new URL(base + "/api/auth/local-token");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Content-Type", "application/json");
        byte[] body = ("{\"username\":\"" + username.replace("\"", "") + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }
        int code = conn.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        if (code >= 400) {
            throw new IllegalStateException("HTTP " + code + " " + sb);
        }
        JsonNode node = objectMapper.readTree(sb.toString());
        if (node.hasNonNull("token")) {
            return node.get("token").asText();
        }
        if (node.hasNonNull("accessToken")) {
            return node.get("accessToken").asText();
        }
        if (node.hasNonNull("access_token")) {
            return node.get("access_token").asText();
        }
        throw new IllegalStateException("no token in response");
    }

    private String jwtSub(String jwt) throws Exception {
        if (jwt == null || jwt.isEmpty()) {
            return null;
        }
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        byte[] decoded = Base64.getUrlDecoder().decode(padBase64(parts[1]));
        JsonNode payload = objectMapper.readTree(decoded);
        return payload.hasNonNull("sub") ? payload.get("sub").asText() : null;
    }

    private static String padBase64(String s) {
        int rem = s.length() % 4;
        if (rem == 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s);
        for (int i = 0; i < 4 - rem; i++) {
            sb.append('=');
        }
        return sb.toString();
    }

    private String extractTemplateId(String configJson) {
        try {
            JsonNode n = objectMapper.readTree(configJson);
            if (n.hasNonNull("template_id")) {
                return n.get("template_id").asText();
            }
            if (n.hasNonNull("templateId")) {
                return n.get("templateId").asText();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }
}
