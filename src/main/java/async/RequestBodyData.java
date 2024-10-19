package async;

import java.util.List;
import java.util.Map;

public class RequestBodyData {
    private List<Message> messages;
    private String model;  // 可选字段
    private Number temperature;  // 支持整数或浮点数
    private Integer max_tokens;  // 字段名与 JSON 保持一致
    private Boolean stream;  // 布尔值，JSON 应该是 true/false
    private Number presence_penalty;  // 支持整数或浮点数
    private Number frequency_penalty;  // 支持整数或浮点数
    private Number top_p;  // 支持整数或浮点数
    private Map<String, Integer> logit_bias;  // 字段名与 JSON 保持一致

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

    public Number getTemperature() {
        return temperature;
    }

    public void setTemperature(Number temperature) {
        this.temperature = temperature;
    }

    public Integer getMax_tokens() {
        return max_tokens;
    }

    public void setMax_tokens(Integer max_tokens) {
        this.max_tokens = max_tokens;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public Number getPresence_penalty() {
        return presence_penalty;
    }

    public void setPresence_penalty(Number presence_penalty) {
        this.presence_penalty = presence_penalty;
    }

    public Number getFrequency_penalty() {
        return frequency_penalty;
    }

    public void setFrequency_penalty(Number frequency_penalty) {
        this.frequency_penalty = frequency_penalty;
    }

    public Number getTop_p() {
        return top_p;
    }

    public void setTop_p(Number top_p) {
        this.top_p = top_p;
    }

    public Map<String, Integer> getLogit_bias() {
        return logit_bias;
    }

    public void setLogit_bias(Map<String, Integer> logit_bias) {
        this.logit_bias = logit_bias;
    }

    @Override
    public String toString() {
        return "RequestBodyData{" +
                "messages=" + messages +
                ", model='" + model + '\'' +
                ", temperature=" + temperature +
                ", max_tokens=" + max_tokens +
                ", stream=" + stream +
                ", presence_penalty=" + presence_penalty +
                ", frequency_penalty=" + frequency_penalty +
                ", top_p=" + top_p +
                ", logit_bias=" + logit_bias +
                '}';
    }
}
