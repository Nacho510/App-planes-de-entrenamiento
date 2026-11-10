package cr.ac.entrenos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
    "spring.datasource.url=jdbc:h2:mem:httpsecurity;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password="
})
class HttpSecurityIntegrationTest {
    @Autowired FilterChainProxy security;
    private int status(String path) throws Exception {
        var request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        var response = new MockHttpServletResponse();
        security.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }
    @Test void publicAssetsAndProtectedApi() throws Exception {
        assertEquals(200, status("/assets/test.txt"));
        assertEquals(401, status("/api/auth/me"));
        assertEquals(401, status("/api/sessions"));
    }
}
