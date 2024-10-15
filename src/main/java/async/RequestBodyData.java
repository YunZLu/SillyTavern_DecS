package async;

import java.util.List;
import java.util.Map;

public class RequestBodyData {
    private List<Message> messages;
    private String model;  // 可选字段
    private Double temperature;  // 可选字段，类型改为 Double，便于判断 null
    private Integer max_tokens;  // 可选字段，类型改为 Integer
    private Boolean stream;  // 可选字段
    private Double presence_penalty;  // 可选字段
    private Double frequency_penalty;  // 可选字段
    private Double top_p;  // 可选字段
    private Map<String, Integer> logit_bias;  // 可选字段，Map 也可以为 null

    // Getter 和 Setter 方法
    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return max_tokens;
    }

    public void setMaxTokens(Integer max_tokens) {
        this.max_tokens = max_tokens;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public Double getPresencePenalty() {
        return presence_penalty;
    }

    public void setPresencePenalty(Double presence_penalty) {
        this.presence_penalty = presence_penalty;
    }

    public Double getFrequencyPenalty() {
        return frequency_penalty;
    }

    public void setFrequencyPenalty(Double frequency_penalty) {
        this.frequency_penalty = frequency_penalty;
    }

    public Double getTopP() {
        return top_p;
    }

    public void setTopP(Double top_p) {
        this.top_p = top_p;
    }

    public Map<String, Integer> getLogitBias() {
        return logit_bias;
    }

    public void setLogitBias(Map<String, Integer> logit_bias) {
        this.logit_bias = logit_bias;
    }
}
