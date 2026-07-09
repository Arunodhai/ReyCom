package com.reydark.reycom;

import com.reydark.reycom.repository.CategoryRepository;
import com.reydark.reycom.repository.ProductRepository;
import com.reydark.reycom.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
class ReycomApplicationTests {

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private CategoryRepository categoryRepository;

    @MockBean
    private ProductRepository productRepository;

    @Test
    void contextLoads() {
    }

}
