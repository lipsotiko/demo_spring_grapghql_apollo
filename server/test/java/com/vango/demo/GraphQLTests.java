package com.vango.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vango.demo.graphql.response.GqlResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class GraphQLTests {

	@Autowired private TestRestTemplate restTemplate;
	@Autowired private HelloWorldRepository helloWorldRepository;
	private HttpHeaders headers = new HttpHeaders();
	private ObjectMapper mapper = new ObjectMapper();

	@Before
	public void setUp() {
		headers.setContentType(MediaType.APPLICATION_JSON);

		for(int i = 1; i <= 10; i++) {
			HelloWorld helloWorld = new HelloWorld(String.format("vango %s", i));
			helloWorldRepository.saveAndFlush(helloWorld);
		}
	}

	@After
	public void tearDown()	 {
		helloWorldRepository.deleteAll();
	}

	@Test
	public void get_helloworlds() throws IOException {
		String query = "{ \"query\": \"query { getHelloWorlds { id name } }\", \"variables\": null }";
		GqlResponse gqlResponse = executeGQL(query);
		assertThat(gqlResponse.data.getHelloWorlds.size()).isEqualTo(10);
	}

	@Test
	public void get_helloworlds_by_name() throws IOException {
		String query = "{ \"query\": \"query getHelloWorldsByName($name: String!) { " +
				"getHelloWorldsByName(name: $name) { id name } } \", \"variables\": {\"name\": \"vango 1\"} }";
		GqlResponse gqlResponse = executeGQL(query);
		assertThat(gqlResponse.data.getHelloWorldsByName.size()).isEqualTo(1);
		assertThat(gqlResponse.data.getHelloWorldsByName.get(0).getName()).isEqualTo("vango 1");
	}

	@Test
	public void save_helloworld() throws IOException {
		String query = "{ \"query\": \"mutation saveHelloWorld($name: String!) { " +
				"saveHelloWorld(helloWorldInput: {name: $name}) { id name } } \", " +
				"\"variables\": {\"name\": \"new vango\"} }";
		GqlResponse gqlResponse = executeGQL(query);
		assertThat(gqlResponse.data.saveHelloWorld.getName()).isEqualTo("new vango");
		assertThat(gqlResponse.data.saveHelloWorld.getId()).isNotNull();
		assertThat(helloWorldRepository.findAll().size()).isEqualTo(11);
		assertThat(helloWorldRepository.findByName("new vango").get(0)).isNotNull();
	}

	@Test
	public void update_helloworld() throws IOException {
		HelloWorld existingHelloWorld = helloWorldRepository.findAll().get(0);
		Long id = existingHelloWorld.getId();
		String query = "{ \"query\": \"mutation saveHelloWorld($name: String!) { " +
				"saveHelloWorld(helloWorldInput: {id: " + id + " name: $name}) { id name } } \", " +
				"\"variables\": {\"name\": \"updated vango\"} }";

		executeGQL(query);

		Optional<HelloWorld> updatedRecord = helloWorldRepository.findById(id);

		if (updatedRecord.isPresent()) {
			assertThat(updatedRecord.get().getName()).isEqualTo("updated vango");
		} else {
			fail("The updated Hello World record could not be found in the database!!!");
		}
	}

	@Test
	public void delete_helloworld() throws IOException {
		String query = "{ \"query\": \"mutation deleteHelloWorlds($name: String!) { " +
				"deleteHelloWorlds(name: $name) { id name } } \", " +
				"\"variables\": {\"name\": \"vango 1\"} }";

		GqlResponse gqlResponse = executeGQL(query);
		Long id = gqlResponse.data.deleteHelloWorlds.get(0).getId();
		assertThat(helloWorldRepository.findById(id).isPresent()).isEqualTo(false);
	}

	private GqlResponse executeGQL(String query) throws IOException {
		HttpEntity<String> httpEntity = new HttpEntity<>(query, headers);
		ResponseEntity<String> exchange = restTemplate.exchange("/graphql", HttpMethod.POST, httpEntity, String.class);
		return mapper.readValue(exchange.getBody(), GqlResponse.class);
	}

}
