package co.ritech.settings;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Installation-level key/value setting (branding, legal texts). */
@Entity
@Table(name = "settings")
public class Setting {

    @Id
    @Column(name = "setting_key", length = 80)
    private String key;

    @Column(name = "setting_value", columnDefinition = "text")
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Setting() {}

    public Setting(String key, String value, Instant now) {
        this.key = key;
        this.value = value;
        this.updatedAt = now;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
