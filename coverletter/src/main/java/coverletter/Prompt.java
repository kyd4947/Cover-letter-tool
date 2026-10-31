package coverletter;

/** LLM 호출 단위 - system(불변 원칙) + user(동적 조립) */
public record Prompt(String systemText, String userText) {}
