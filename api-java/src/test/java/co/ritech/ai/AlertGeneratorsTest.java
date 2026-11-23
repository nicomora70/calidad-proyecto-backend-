package co.ritech.ai;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertGeneratorsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static AlertContext context(String variation, String returnPct, String finalAmount, String profit) {
        return new AlertContext(
                "QQQ",
                LocalDate.of(2026, 11, 20),
                new BigDecimal(variation),
                new BigDecimal("2"),
                new BigDecimal(returnPct),
                new BigDecimal("1000000.00"),
                "COP",
                new BigDecimal(finalAmount),
                new BigDecimal(profit));
    }

    @Test
    void templateDescribesGain() {
        var alert = new TemplateAiAlertGenerator().generate(context("1.5", "3", "1030000.00", "30000.00"));

        assertThat(alert.title()).isEqualTo("El NASDAQ 100 subió 1,50 % y tu simulación ganó 3,00 %");
        assertThat(alert.content())
                .contains("1.000.000,00 COP quedó en 1.030.000,00 COP, una ganancia de 30.000,00 COP")
                .contains("Recuerda que el apalancamiento");
    }

    @Test
    void templateDescribesLossAndWarnsOnHighVariation() {
        var alert = new TemplateAiAlertGenerator().generate(context("-2", "-4", "960000.00", "-40000.00"));

        assertThat(alert.title()).isEqualTo("El NASDAQ 100 bajó 2,00 % y tu simulación perdió 4,00 %");
        assertThat(alert.content()).contains("una pérdida de 40.000,00 COP").contains("La variación fue alta");
    }

    @Test
    void templateHandlesZeroVariation() {
        var alert = new TemplateAiAlertGenerator().generate(context("0", "0", "1000000.00", "0.00"));
        assertThat(alert.title()).isEqualTo("El NASDAQ 100 no tuvo variación");
    }

    @Test
    void promptIncludesSimulationData() {
        String prompt = AlertPrompt.render(context("1.5", "3", "1030000.00", "30000.00"));
        assertThat(prompt).contains("1.5 %").contains("1030000.00 COP").doesNotContain("{{");
    }

    @Test
    void parsesGeminiResponse() throws Exception {
        var body = mapper.readTree("""
                {"candidates":[{"content":{"parts":[{"text":"{\\"title\\":\\"Subida\\",\\"content\\":\\"Texto\\"}"}]}}],
                 "usageMetadata":{"promptTokenCount":180,"candidatesTokenCount":60}}""");

        var alert = GeminiAiAlertGenerator.parse(body, "gemini-2.5-flash", mapper);

        assertThat(alert.title()).isEqualTo("Subida");
        assertThat(alert.content()).isEqualTo("Texto");
        assertThat(alert.promptTokens()).isEqualTo(180);
        assertThat(alert.completionTokens()).isEqualTo(60);
        assertThat(alert.promptVersion()).isEqualTo(AlertPrompt.VERSION);
    }

    @Test
    void parsesGeminiJsonWrappedInMarkdownFence() throws Exception {
        var body = mapper.readTree("""
                {"candidates":[{"content":{"parts":[{"text":"```json\\n{\\"title\\":\\"T\\",\\"content\\":\\"C\\"}\\n```"}]}}]}""");
        assertThat(GeminiAiAlertGenerator.parse(body, "m", mapper).title()).isEqualTo("T");
    }

    @Test
    void rejectsGeminiResponseWithoutText() throws Exception {
        var body = mapper.readTree("{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}");
        assertThatThrownBy(() -> GeminiAiAlertGenerator.parse(body, "m", mapper))
                .isInstanceOf(AiAlertException.class);
    }

    @Test
    void rejectsGeminiTextThatIsNotJson() throws Exception {
        var body = mapper.readTree("""
                {"candidates":[{"content":{"parts":[{"text":"Hoy el mercado subió"}]}}]}""");
        assertThatThrownBy(() -> GeminiAiAlertGenerator.parse(body, "m", mapper))
                .isInstanceOf(AiAlertException.class)
                .hasMessageContaining("not valid JSON");
    }

    @Test
    void rejectsGeminiJsonWithoutContent() throws Exception {
        var body = mapper.readTree("""
                {"candidates":[{"content":{"parts":[{"text":"{\\"title\\":\\"T\\"}"}]}}]}""");
        assertThatThrownBy(() -> GeminiAiAlertGenerator.parse(body, "m", mapper))
                .isInstanceOf(AiAlertException.class);
    }
}
