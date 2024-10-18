package async;

import java.util.List;
import java.util.Map;

public class RequestBodyData {
    private List<Message> messages;
    private String model;  // 可选字段
    private Double temperature;  // 可选字段
    private Integer maxTokens;  // 可选字段，使用驼峰命名法
    private Boolean stream;  // 可选字段
    private Double presencePenalty;  // 可选字段
    private Double frequencyPenalty;  // 可选字段
    private Double topP;  // 可选字段
    private Map<String, Integer> logitBias;  // 可选字段，Map 也可以为 null

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
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public Double getPresencePenalty() {
        return presencePenalty;
    }

    public void setPresencePenalty(Double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    public Double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public void setFrequencyPenalty(Double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Map<String, Integer> getLogitBias() {
        return logitBias;
    }

    public void setLogitBias(Map<String, Integer> logitBias) {
        this.logitBias = logitBias;
    }

    // 增加 toString() 方法，便于日志输出
    @Override
    public String toString() {
        return "RequestBodyData{" +
                "messages=" + messages +
                ", model='" + model + '\'' +
                ", temperature=" + temperature +
                ", maxTokens=" + maxTokens +
                ", stream=" + stream +
                ", presencePenalty=" + presencePenalty +
                ", frequencyPenalty=" + frequencyPenalty +
                ", topP=" + topP +
                ", logitBias=" + logitBias +
                '}';
    }
}
