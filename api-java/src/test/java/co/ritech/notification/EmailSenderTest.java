package co.ritech.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import co.ritech.TestFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailSenderTest {

    private static SpringTemplateEngine engine() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    @SuppressWarnings("unchecked")
    private static EmailSender sender(String host) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(JavaMailSender.class));
        var env = new MockEnvironment();
        if (host != null) {
            env.setProperty("spring.mail.host", host);
        }
        return new EmailSender(provider, engine(), env, TestFixtures.properties());
    }

    @Test
    void blankHostDisablesEmail() {
        assertThat(sender("").available()).isFalse();
        assertThat(sender(null).available()).isFalse();
        assertThat(sender("smtp.cliente.co").available()).isTrue();
    }

    @Test
    void sendingWithoutSmtpFailsClearly() {
        assertThatThrownBy(() -> sender("").send("a@b.co", "T", "B")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void templateEscapesContentAndIncludesDisclaimer() {
        String html = sender("smtp.cliente.co").render("Alerta <b>lista</b>", "Texto & más");

        assertThat(html)
                .contains("Alerta &lt;b&gt;lista&lt;/b&gt;")
                .contains("Texto &amp; más")
                .contains("no constituye asesoría financiera");
    }
}
