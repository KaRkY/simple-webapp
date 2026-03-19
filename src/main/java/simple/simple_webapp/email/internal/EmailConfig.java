package simple.simple_webapp.email.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

@Configuration
class EmailConfig {

    @Bean
    TemplateEngine textEmailTemplateEngine() {
        var resolver = new StringTemplateResolver();
        resolver.setCacheable(false);
        resolver.setTemplateMode(TemplateMode.TEXT);

        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    @Bean
    TemplateEngine htmlEmailTemplateEngine() {
        var resolver = new StringTemplateResolver();
        resolver.setCacheable(false);
        resolver.setTemplateMode(TemplateMode.HTML);

        var engine = new SpringTemplateEngine();
        engine.setEnableSpringELCompiler(true);
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
