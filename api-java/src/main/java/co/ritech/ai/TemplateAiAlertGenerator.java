package co.ritech.ai;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Rule-based alert without any external call: default provider, used in CI, demos and
 * installations without an AI key. Also serves as the reference output for the LLM providers.
 */
@Component
@ConditionalOnProperty(name = "ritech.ai.provider", havingValue = "template", matchIfMissing = true)
public class TemplateAiAlertGenerator implements AiAlertGenerator {

    static final String MODEL = "template";
    private static final BigDecimal HIGH_RETURN_PCT = new BigDecimal("4");
    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");

    @Override
    public GeneratedAlert generate(AlertContext c) {
        int direction = c.variationPct().signum();
        String title = switch (direction) {
            case 1 ->
                "El NASDAQ 100 subió " + fmt(c.variationPct()) + " % y tu simulación ganó " + fmt(c.returnPct()) + " %";
            case -1 ->
                "El NASDAQ 100 bajó " + fmt(c.variationPct().abs()) + " % y tu simulación perdió "
                        + fmt(c.returnPct().abs()) + " %";
            default -> "El NASDAQ 100 no tuvo variación";
        };
        String outcome = c.profit().signum() < 0 ? "una pérdida" : "una ganancia";
        String risk = c.returnPct().abs().compareTo(HIGH_RETURN_PCT) >= 0
                ? " La variación fue alta: el apalancamiento multiplica tanto las ganancias como las pérdidas."
                : " Recuerda que el apalancamiento multiplica tanto las ganancias como las pérdidas.";
        String content = "El " + c.marketDate() + " el NASDAQ 100 (proxy " + c.symbol() + ") varió "
                + fmt(c.variationPct()) + " %. Con apalancamiento "
                + c.leverage().stripTrailingZeros().toPlainString()
                + "x, tu capital de " + fmt(c.initialCapital()) + " " + c.currency() + " quedó en "
                + fmt(c.finalAmount()) + " " + c.currency() + ", " + outcome + " de "
                + fmt(c.profit().abs()) + " " + c.currency() + "." + risk;
        return new GeneratedAlert(title, content, MODEL, "template-v1", 0, 0);
    }

    @Override
    public boolean billable() {
        return false;
    }

    /** Two decimals with Colombian separators: 1.030.000,00 */
    private static String fmt(BigDecimal value) {
        NumberFormat f = NumberFormat.getNumberInstance(ES_CO);
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return f.format(value);
    }
}
