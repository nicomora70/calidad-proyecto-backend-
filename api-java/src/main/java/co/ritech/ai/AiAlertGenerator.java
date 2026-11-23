package co.ritech.ai;

/** Produces the alert text for a simulation. Implementations are swappable with AI_PROVIDER. */
public interface AiAlertGenerator {

    /**
     * @throws AiAlertException if the alert cannot be produced (provider down, invalid response)
     */
    GeneratedAlert generate(AlertContext context);

    /** Whether calls cost money and therefore count against the monthly budget. */
    default boolean billable() {
        return true;
    }
}
