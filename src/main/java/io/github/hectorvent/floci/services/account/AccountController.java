package io.github.hectorvent.floci.services.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RequestContext;
import io.github.hectorvent.floci.core.storage.StorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AccountController {
    private final ObjectMapper mapper;
    private final RequestContext context;
    private final StorageBackend<String, AlternateContact> contacts;

    @Inject
    public AccountController(ObjectMapper mapper, RequestContext context, StorageFactory storageFactory) {
        this.mapper = mapper;
        this.context = context;
        this.contacts = storageFactory.create("account", "account-alternate-contacts.json",
                new TypeReference<Map<String, AlternateContact>>() {});
    }

    @POST
    @Path("/putAlternateContact")
    public Response put(String body) {
        JsonNode input = parse(body);
        String type = required(input, "AlternateContactType");
        AlternateContact contact = new AlternateContact(type, required(input, "Name"), required(input, "Title"),
                required(input, "EmailAddress"), required(input, "PhoneNumber"));
        contacts.put(context.getAccountId() + "::" + type, contact);
        return Response.ok(mapper.createObjectNode()).build();
    }

    @POST
    @Path("/getAlternateContact")
    public Response get(String body) {
        JsonNode input = parse(body);
        String type = required(input, "AlternateContactType");
        AlternateContact contact = contacts.get(context.getAccountId() + "::" + type)
                .orElseThrow(() -> new AwsException("ResourceNotFoundException", "Alternate contact not found.", 404));
        var out = mapper.createObjectNode();
        var node = out.putObject("AlternateContact");
        node.put("AlternateContactType", contact.type()); node.put("Name", contact.name()); node.put("Title", contact.title());
        node.put("EmailAddress", contact.email()); node.put("PhoneNumber", contact.phone());
        return Response.ok(out).build();
    }

    private JsonNode parse(String body) {
        try { return mapper.readTree(body == null || body.isBlank() ? "{}" : body); }
        catch (Exception e) { throw new AwsException("ValidationException", "Request body is not valid JSON.", 400); }
    }
    private static String required(JsonNode input, String field) {
        JsonNode value = input.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) throw new AwsException("ValidationException", field + " is required.", 400);
        return value.textValue();
    }
    public record AlternateContact(String type, String name, String title, String email, String phone) {}
}
