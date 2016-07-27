package au.com.mountainpass.hyperstate.server;

import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.script.ScriptException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.HandlerMapping;

import au.com.mountainpass.hyperstate.core.Action;
import au.com.mountainpass.hyperstate.core.EntityRepository;
import au.com.mountainpass.hyperstate.core.MediaTypes;
import au.com.mountainpass.hyperstate.core.entities.Entity;
import au.com.mountainpass.hyperstate.core.entities.EntityWrapper;

public abstract class HyperstateController {

    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private EntityRepository repository;

    public HyperstateController() {

    }

    @RequestMapping(value = "**", method = RequestMethod.DELETE, produces = {
            "application/vnd.siren+json",
            "application/json" }, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ResponseBody
    @Async
    public CompletableFuture<ResponseEntity<?>> delete(
            final HttpServletRequest request)
                    throws URISyntaxException, NoSuchMethodException,
                    SecurityException, ScriptException, IllegalAccessException,
                    IllegalArgumentException, InvocationTargetException,
                    InterruptedException, ExecutionException {
        final String url = (String) request.getAttribute(
                HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        return getEntity(url).thenApplyAsync(entity -> {
            if (entity == null) {
                return ResponseEntity.noContent().build();
            }
            final Optional<Action<?>> actionOptional = entity.getActions()
                    .stream()
                    .filter(e -> e.getNature().equals(HttpMethod.DELETE))
                    .findAny();

            if (!actionOptional.isPresent()) {
                deleteEntity(entity);
            } else {
                try {
                    final Action<?> action = actionOptional.get();
                    final CompletableFuture<?> invocationResult = action
                            .invoke(new HashMap<>());
                    invocationResult.join();
                } catch (final Exception e) {
                    LOGGER.error(e.getMessage(), e);
                    return ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
            }
            return ResponseEntity.noContent().build();
        });
    }

    protected CompletableFuture<Void> deleteEntity(
            final EntityWrapper<?> entity) {
        return repository.delete(entity);
    }

    @RequestMapping(value = "**", method = RequestMethod.GET, produces = {
            MediaTypes.SIREN_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE })
    @ResponseBody
    @Async
    public CompletableFuture<ResponseEntity<?>> get(
            @RequestParam final Map<String, Object> allRequestParams,
            final HttpServletRequest request) {
        String url = (String) request.getAttribute(
                HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (!allRequestParams.isEmpty()) {
            url += "?" + request.getQueryString();
        }
        final RequestAttributes currentRequestAttributes = RequestContextHolder
                .getRequestAttributes();
        return getEntity(url).thenApplyAsync(entity -> {
            RequestContextHolder.setRequestAttributes(currentRequestAttributes);
            if (entity == null) {
                return ResponseEntity.notFound().build();
            } else {
                return ResponseEntity.ok(entity);
            }
        });
    }

    protected CompletableFuture<EntityWrapper<?>> getEntity(
            final String identifier) {
        final RequestMapping requestMapping = AnnotationUtils
                .findAnnotation(this.getClass(), RequestMapping.class);
        return repository.findOne(identifier);
    }

    public CompletableFuture<EntityWrapper<?>> getRoot() {
        return getEntity(getRootPath());
    }

    private String getRootPath() {
        final RequestMapping requestMapping = AnnotationUtils
                .findAnnotation(this.getClass(), RequestMapping.class);

        return requestMapping.value()[0];
    }

    @RequestMapping(value = "**", method = RequestMethod.GET, produces = {
            "text/html", "application/xhtml+xml" })
    public String html(final HttpServletRequest request) {
        final String path = (String) request.getAttribute(
                HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if ("/index.html".equals(path)) {
            throw new NotImplementedException(
                    "eeeek! Looks like you've created a "
                            + HyperstateController.class.getSimpleName()
                            + " without a context path. We don't support that yet.");
        }
        return "/index.html";
    }

    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<?> onException(final Exception e) {
        LOGGER.error(e.getLocalizedMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @RequestMapping(value = "**", method = RequestMethod.POST, produces = {
            "application/vnd.siren+json",
            "application/json" }, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ResponseBody
    public ResponseEntity<?> post(
            @RequestParam final MultiValueMap<String, Object> allRequestParams,
            final HttpServletRequest request)
                    throws URISyntaxException, NoSuchMethodException,
                    SecurityException, ScriptException, IllegalAccessException,
                    IllegalArgumentException, InvocationTargetException,
                    InterruptedException, ExecutionException {
        final String path = (String) request.getAttribute(
                HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        final EntityWrapper<?> entity = getEntity(path).get();
        if (entity == null) {
            return ResponseEntity.notFound().build();
        }

        final Object actionName = allRequestParams.getFirst("action");
        if (actionName == null) {
            // todo add body with classes indicating what is missing
            return ResponseEntity.badRequest().build();
        }
        final Action<?> action = entity.getAction(actionName.toString());
        if (action == null) {
            // todo add body with classes indicating what is missing
            return ResponseEntity.badRequest().build();
        }
        // todo: post actions should have a link return value
        // todo: automatically treat actions that return links as POST actions
        final Entity result = (Entity) action
                .invoke(allRequestParams.toSingleValueMap()).get();
        return ResponseEntity.created(result.getAddress()).build();
    }

    @RequestMapping(value = "**", method = RequestMethod.PUT, produces = {
            "application/vnd.siren+json",
            "application/json" }, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ResponseBody
    public ResponseEntity<?> put(
            @RequestParam final MultiValueMap<String, Object> queryParams,

    final HttpServletRequest request) throws IllegalAccessException,
            IllegalArgumentException, InvocationTargetException,
            URISyntaxException, InterruptedException, ExecutionException {
        final String url = (String) request.getAttribute(
                HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        final EntityWrapper<?> entity = getEntity(url).get();
        if (entity == null) {
            return ResponseEntity.notFound().build();
        }
        final MultiValueMap<String, Object> params = new LinkedMultiValueMap<>();
        params.putAll(queryParams);
        final String actionName = (String) queryParams.getFirst("action");
        if (actionName == null) {
            // todo add body with classes indicating what is missing
            return ResponseEntity.badRequest().build();
        }
        final au.com.mountainpass.hyperstate.core.Action<?> action = entity
                .getAction(actionName);
        if (action == null) {
            // todo add body with classes indicating what is missing
            return ResponseEntity.badRequest().build();
        }

        action.invoke(params.toSingleValueMap());
        // todo: automatically treat actions that return void as PUT actions
        return ResponseEntity.noContent().location(entity.getAddress()).build();
    }

}