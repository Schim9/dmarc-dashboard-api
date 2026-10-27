package lu.kaminski.dmarc;

import lu.kaminski.dmarc.scheduler.DmarcScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "dmarc.mail.host=localhost",
        "dmarc.mail.username=test@test.com",
        "dmarc.mail.password=test",
        "dmarc.scheduler.enabled=false",
        "dmarc.source.mode=folder",
        "dmarc.source.folder.path=./test-dmarc-reports",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "dmarc.jwt.secret=test-secret-key-for-tests-only-32chars!!",
        "dmarc.admin.password=test"
})
class DmarcDashboardApiApplicationTests {

    @MockitoBean
    DmarcScheduler dmarcScheduler;

    @Test
    void contextLoads() {
    }
}
