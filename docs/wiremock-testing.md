# Using WireMock for Integration Testing

This document explains how to use WireMock to mock external API dependencies in the Spring PetClinic microservices project.

## Overview

WireMock is a library for stubbing and mocking web services. It can be used to simulate external API dependencies in integration tests, allowing you to test your application without relying on actual external services.

In this project, we've implemented WireMock to mock the Visits service in the API Gateway's integration tests.

## Dependencies

To use WireMock in your tests, you need to add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-contract-stub-runner</artifactId>
    <scope>test</scope>
</dependency>
```

This dependency includes WireMock and the Spring Cloud Contract Stub Runner, which provides integration with Spring Boot's test framework.

## Standalone WireMock Tests

For standalone tests that don't require the Spring Boot context, you can use the `WireMockExtension` JUnit 5 extension. Here's an example:

```java
@RegisterExtension
static WireMockExtension wireMockServer = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

@BeforeEach
void setUp() {
    // Configure your client to use the WireMock server
    client.setBaseUrl(wireMockServer.baseUrl());
}

@Test
void testWithWireMock() {
    // Setup the WireMock stub
    wireMockServer.stubFor(get(urlPathEqualTo("/api/resource"))
            .withQueryParam("param", equalTo("value"))
            .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"key\":\"value\"}")));

    // Call your client
    Response response = client.getResource("value");

    // Verify the response
    assertEquals("value", response.getKey());

    // Verify that the expected request was made to the mock server
    wireMockServer.verify(getRequestedFor(urlPathEqualTo("/api/resource"))
            .withQueryParam("param", equalTo("value")));
}
```

See `VisitsServiceClientWireMockTest.java` for a complete example.

## Spring Boot Integration Tests with WireMock

For integration tests that require the Spring Boot context, you can use the `@AutoConfigureWireMock` annotation. Here's an example:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWireMock(port = 0) // Use a random port
@TestPropertySource(properties = {
    "spring.cloud.discovery.enabled=false",
    "spring.cloud.config.enabled=false",
    "spring.cloud.loadbalancer.enabled=false"
})
class ServiceIntegrationTest {

    @Autowired
    private YourClient client;
    
    @Value("${wiremock.server.port}")
    private int wireMockPort;

    @BeforeEach
    void setUp() {
        // Reset WireMock to ensure clean state for each test
        WireMock.reset();
        
        // Configure your client to use the WireMock server
        client.setBaseUrl("http://localhost:" + wireMockPort);
    }

    @Test
    void testWithWireMock() {
        // Setup the WireMock stub
        stubFor(get(urlPathEqualTo("/api/resource"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"key\":\"value\"}")));

        // Call your client
        Response response = client.getResource();

        // Verify the response
        assertEquals("value", response.getKey());

        // Verify that the expected request was made to the mock server
        verify(getRequestedFor(urlPathEqualTo("/api/resource")));
    }
}
```

See `VisitsServiceSpringBootWireMockTest.java` for a complete example.

## Test Configuration

To configure your application for testing with WireMock, you can create an `application-test.yml` file in your test resources directory:

```yaml
spring:
  cloud:
    config:
      enabled: false
    discovery:
      enabled: false
    loadbalancer:
      enabled: false
  main:
    allow-bean-definition-overriding: true

# Disable Eureka client
eureka:
  client:
    enabled: false
    register-with-eureka: false
    fetch-registry: false

# Logging configuration for tests
logging:
  level:
    org.springframework.web: INFO
    org.springframework.samples.petclinic: DEBUG
    # WireMock logging
    com.github.tomakehurst.wiremock: DEBUG
```

This configuration disables Spring Cloud Config, Discovery, and Load Balancer, which can interfere with WireMock testing.

## Best Practices

1. **Use URL Path Matching**: When setting up stubs, use `urlPathEqualTo` instead of `urlEqualTo` to match only the path part of the URL. This is more flexible and handles URL encoding of query parameters.

2. **Separate Query Parameter Matching**: Use `.withQueryParam("name", equalTo("value"))` to match query parameters separately from the path.

3. **Reset WireMock Between Tests**: Call `WireMock.reset()` in your `@BeforeEach` method to ensure a clean state for each test.

4. **Verify Requests**: Use `verify` to ensure that the expected requests were made to the mock server.

5. **Handle URL Encoding**: Be aware that query parameters are URL-encoded in requests. For example, a comma (`,`) is encoded as `%2C`.

## Examples

For complete examples of using WireMock in this project, see:

- `VisitsServiceClientWireMockTest.java`: A standalone test that uses WireMock to mock the Visits service.
- `VisitsServiceSpringBootWireMockTest.java`: A Spring Boot integration test that uses WireMock with the Spring Boot test framework.
- `application-test.yml`: Configuration for testing with WireMock.

## Troubleshooting

If you encounter issues with WireMock in your tests, check the following:

1. **URL Matching**: Ensure that your URL matchers (e.g., `urlPathEqualTo`, `urlEqualTo`) match the actual requests being made.

2. **Query Parameters**: Be aware that query parameters are URL-encoded in requests. Use separate query parameter matchers for more flexibility.

3. **Service Resolution**: If you're using Spring Cloud's service discovery, ensure it's properly disabled in your test configuration.

4. **Load Balancing**: If you're using Spring Cloud's load balancer, ensure it's properly disabled in your test configuration.

5. **Logging**: Enable debug logging for WireMock to see detailed information about requests and responses.

## Conclusion

WireMock is a powerful tool for mocking external API dependencies in integration tests. By using WireMock, you can test your application without relying on actual external services, making your tests more reliable and faster.

For more information about WireMock, see the [official documentation](http://wiremock.org/docs/).
