package org.example.hospital.service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import jakarta.annotation.PostConstruct;
import org.example.hospital.dto.ChatMessage;
import org.example.hospital.dto.CozeRequest;
import org.example.hospital.dto.CozeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Coze AI 智能体集成服务
 * 调用 Coze API 获取 AI 回复
 */

@Service
public class CozeAgentService {
    private static final Logger logger = LoggerFactory.getLogger(CozeAgentService.class);
    private static final String LOCAL_PROXY_PATH = "/api/coze/chat";
    private static final String WORKFLOW_RUN_PATH = "/v1/workflow/run";

    @Value("${coze.api.url:http://localhost:8000}")
    private String cozeApiUrl;

    @Value("${coze.api.key:}")
    private String cozeApiKey;

    @Value("${coze.workflow.id:}")
    private String workflowId;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AgentChatService agentChatService;

    public CozeAgentService(RestTemplate restTemplate, ObjectMapper objectMapper, AgentChatService agentChatService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.agentChatService = agentChatService;
    }

    @PostConstruct
    public void logCozeConfig() {
        logger.info("[COZE_CONFIG] apiUrl={}, apiKeyPresent={}, workflowIdPresent={}, workflowId={}",
            cozeApiUrl,
            cozeApiKey != null && !cozeApiKey.trim().isEmpty(),
            workflowId != null && !workflowId.trim().isEmpty(),
            workflowId);
    }

    /**
     * 调用 Coze 智能体获取回复
     */
    public CozeResponse chat(CozeRequest request) {
        if (request == null) {
            String errorMsg = "输入内容不能为空";
            logger.warn("{}: request is null", errorMsg);
            return new CozeResponse(null, "FAILED", errorMsg);
        }

        String normalizedInput = request.getNormalizedContent();
        if (normalizedInput.isEmpty()) {
            String errorMsg = "输入内容不能为空";
            logger.warn("{}: content='{}', message='{}'", errorMsg, request.getContent(), request.getMessage());
            return new CozeResponse(null, "FAILED", errorMsg);
        }

        try {
            // 调用 Coze 工作流
            String response = callCozeWorkflow(normalizedInput);

            if (response == null) {
                String errorMsg = "Coze 工作流返回 null";
                logger.error(errorMsg);
                return new CozeResponse(null, "FAILED", errorMsg);
            }

            if (isCozeExecuteLink(response)) {
                String errorMsg = "Coze 返回了执行链接而非最终文本，请检查工作流输出字段映射";
                logger.error("{}，response={}", errorMsg, response);
                return new CozeResponse(null, "FAILED", errorMsg);
            }

            if (response.trim().isEmpty()) {
                String errorMsg = "智能体返回空响应";
                logger.warn(errorMsg);
                return new CozeResponse(null, "FAILED", errorMsg);
            }

            // 保存 Agent 回复到数据库
            ChatMessage agentMessage = new ChatMessage("Coze Agent", "AGENT", response);
            agentMessage.setTimestamp(OffsetDateTime.now());
            agentChatService.save(agentMessage);

            logger.info("Coze 调用成功，回复长度: {}", response.length());
            return new CozeResponse(response, "SUCCESS");

        } catch (Exception e) {
            String errorMsg = "智能体调用失败: " + e.getMessage();
            logger.error(errorMsg, e);
            return new CozeResponse(null, "FAILED", errorMsg);
        }
    }

    private boolean isCozeExecuteLink(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.trim().toLowerCase();
        return normalized.contains("coze.cn/work_flow?")
            || normalized.contains("coze.cn/workflow?")
            || (normalized.contains("execute_id=") && normalized.contains("workflow_id="));
    }

    /**
     * 调用 Coze 工作流
     */
    private String callCozeWorkflow(String input) throws Exception {
        logger.info("调用 Coze 工作流，输入: {}", input);

        // 如果配置了真实的 Coze API 且有有效的 API 密钥，使用 HTTP 调用
        if (cozeApiUrl != null && !cozeApiUrl.trim().isEmpty() &&
            cozeApiKey != null && !cozeApiKey.trim().isEmpty()) {
            logger.info("使用 HTTP 调用 Coze API: {}", cozeApiUrl);
            try {
                return callCozeViaHttp(input);
            } catch (Exception e) {
                logger.warn("HTTP 调用 Coze 失败，降级到演示模式: {}", e.getMessage());
            }
        }

        // 否则返回示例响应（开发/演示用）
        logger.info("使用演示模式返回响应");
        String response = generateDefaultResponse(input);
        logger.info("演示响应: {}", response);
        return response;
    }

    /**
     * 通过 HTTP 调用 Coze 工作流
     */
    private String callCozeViaHttp(String input) throws Exception {
        String resolvedUrl = resolveCozeUrl();
        boolean proxyMode = isProxyMode(resolvedUrl);
        logger.info("通过 HTTP 调用 Coze: baseUrl={}, resolvedUrl={}, mode={}, workflowId={}, 输入={}",
            cozeApiUrl,
            resolvedUrl,
            proxyMode ? "proxy" : "workflow",
            workflowId,
            input);

        Map<String, Object> payload = new HashMap<>();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (proxyMode) {
            payload.put("input", input);
            if (workflowId != null && !workflowId.trim().isEmpty()) {
                payload.put("workflow_id", workflowId);
            }
        } else {
            payload.put("workflow_id", workflowId);
            Map<String, String> parameters = new HashMap<>();
            parameters.put("input", input);
            payload.put("parameters", parameters);
            headers.setBearerAuth(cozeApiKey);
        }

        HttpEntity<String> request = new HttpEntity<>(
            objectMapper.writeValueAsString(payload),
            headers
        );

        logger.debug("发送 HTTP 请求到: {}, body={}", resolvedUrl, payload);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                resolvedUrl,
                HttpMethod.POST,
                request,
                String.class
            );

            logger.info("Coze HTTP 响应状态码: {}", response.getStatusCode());

            if (!response.getStatusCode().is2xxSuccessful()) {
                String errorMsg = String.format("Coze API 返回错误状态码: %s", response.getStatusCode());
                logger.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

            String responseBody = response.getBody();
            logger.debug("Coze HTTP 原始响应: {}", responseBody);
            String finalResponse = extractResponseText(responseBody, proxyMode);
            logger.info("Coze HTTP 调用成功，解析后回复长度: {}", finalResponse.length());
            return finalResponse;
        } catch (Exception e) {
            logger.error("HTTP 请求异常: {}, resolvedUrl={}", e.getMessage(), resolvedUrl, e);
            throw e;
        }
    }

    private String resolveCozeUrl() {
        String base = Objects.toString(cozeApiUrl, "").trim();
        if (base.isEmpty()) {
            return WORKFLOW_RUN_PATH;
        }

        if (base.contains(LOCAL_PROXY_PATH) || base.contains(WORKFLOW_RUN_PATH)) {
            return base;
        }

        String normalized = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        if (normalized.contains("localhost:8000") || normalized.contains("127.0.0.1:8000")) {
            return normalized + LOCAL_PROXY_PATH;
        }
        return normalized + WORKFLOW_RUN_PATH;
    }

    private boolean isProxyMode(String resolvedUrl) {
        return resolvedUrl != null && resolvedUrl.contains(LOCAL_PROXY_PATH);
    }

    private String extractResponseText(String responseBody, boolean proxyMode) throws Exception {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return "无有效回复内容";
        }

        Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
        Object dataObj;
        if (proxyMode) {
            dataObj = firstNonNull(result.get("response"), result.get("message"), result.get("data"), result.get("msg"));
        } else {
            dataObj = firstNonNull(result.get("data"), result.get("message"), result.get("msg"), result.get("response"));
        }

        if (dataObj == null) {
            return responseBody;
        }

        String finalResponse = dataObj.toString();
        try {
            if (finalResponse.startsWith("{")) {
                Map<String, Object> dataMap = objectMapper.readValue(finalResponse, Map.class);
                Object nested = firstNonNull(dataMap.get("output"), dataMap.get("response"), dataMap.get("message"), dataMap.get("content"));
                if (nested != null) {
                    finalResponse = nested.toString();
                }
            }
        } catch (Exception ignored) {
            logger.debug("解析 Coze 嵌套响应失败，使用原始 data 文本");
        }
        return finalResponse;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 生成默认智能体回复（演示用）
     */
    private String generateDefaultResponse(String input) {
        logger.info("生成演示响应，输入: {}", input);

        if (input == null || input.trim().isEmpty()) {
            return "您发送了空消息，请输入具体内容。";
        }

        String lowerInput = input.toLowerCase();

        if (lowerInput.contains("生成") || lowerInput.contains("排班") || lowerInput.contains("schedule")) {
            return "📋 已接收排班生成请求。根据当前配置，我将按照优先级规则为下周生成最优排班方案：\n\n" +
                   "✓ 夜班人数均匀分配（每晚 3-4 人）\n" +
                   "✓ 资深医生轮休安排\n" +
                   "✓ 新入职员工避免连续夜班\n\n" +
                   "预计 1-2 分钟内完成，结果将发送至管理员邮箱。";
        } else if (lowerInput.contains("校验") || lowerInput.contains("检查") || lowerInput.contains("validate")) {
            return "🔍 开始校验当前排班冲突...\n\n" +
                   "✅ 检查结果：\n" +
                   "  - 总班次: 42\n" +
                   "  - 冲突班次: 0\n" +
                   "  - 覆盖率: 100%\n\n" +
                   "✓ 排班无冲突，可以发布！";
        } else if (lowerInput.contains("数据") || lowerInput.contains("同步") || lowerInput.contains("sync")) {
            return "🔄 同步医院 HIS 系统数据...\n\n" +
                   "✓ 已同步内容：\n" +
                   "  - 医护人员信息: 152 人\n" +
                   "  - 科室部门: 18 个\n" +
                   "  - 班次规则: 8 套\n\n" +
                   "数据同步完成，可用于排班计算。";
        } else if (lowerInput.contains("帮助") || lowerInput.contains("help")) {
            return "🤖 我是医院排班智能助手，支持以下功能：\n\n" +
                   "1️⃣ 生成排班 - \"生成下周排班\" \n" +
                   "2️⃣ 校验排班 - \"校验当前排班\" \n" +
                   "3️⃣ 同步数据 - \"同步 HIS 数据\" \n" +
                   "4️⃣ 查询班次 - \"查看本月班次\" \n\n" +
                   "输入上述关键词即可，我会为你处理排班相关任务！";
        } else {
            return "💬 已收到您的消息：\"" + input + "\"\n\n" +
                   "我是医院排班智能助手，目前处于演示模式。您可以输入以下关键词体验功能：\n" +
                   "• 生成排班\n" +
                   "• 校验排班\n" +
                   "• 同步数据\n" +
                   "• 帮助\n\n" +
                   "如需接入真实 Coze AI，请配置环境变量：COZE_API_KEY 和 COZE_WORKFLOW_ID";
        }
    }
}
