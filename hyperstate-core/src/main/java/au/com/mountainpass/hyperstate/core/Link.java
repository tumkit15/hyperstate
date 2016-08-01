package au.com.mountainpass.hyperstate.core;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import au.com.mountainpass.hyperstate.core.entities.CreatedEntity;
import au.com.mountainpass.hyperstate.core.entities.EntityWrapper;
import au.com.mountainpass.hyperstate.core.entities.UpdatedEntity;

@JsonInclude(JsonInclude.Include.NON_NULL)
abstract public class Link extends Labelled {

    public Link(@JsonProperty("title") final String label,
            @JsonProperty("class") final String... natures) {
        super(label, natures);
    }

    public Link() {
    }

    @JsonProperty("type")
    public abstract MediaType getRepresentationFormat();

    public abstract <T> CompletableFuture<T> resolve(Class<T> type);

    public abstract <T> CompletableFuture<T> resolve(
            ParameterizedTypeReference<T> type);

    public abstract String getPath();

    public abstract CompletableFuture<EntityWrapper<?>> get(
            Map<String, Object> filteredParameters);

    public abstract CompletableFuture<Void> delete(
            Map<String, Object> filteredParameters);

    public abstract CompletableFuture<CreatedEntity> create(
            Map<String, Object> filteredParameters);

    public abstract CompletableFuture<UpdatedEntity> update(
            Map<String, Object> filteredParameters);

    public abstract CompletableFuture<EntityWrapper<?>> get();
}
