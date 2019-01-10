package com.elepy.routes;

import com.elepy.concepts.ObjectEvaluator;
import com.elepy.dao.Crud;
import com.elepy.dao.Page;
import com.elepy.di.ElepyContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import spark.Request;
import spark.Response;

import java.util.List;
import java.util.Optional;

public interface FindHandler<T> extends RouteHandler<T> {
    Page<T> find(Request request, Response response, Crud<T> dao, ObjectMapper objectMapper);

    Optional<T> findOne(Request request, Response response, Crud<T> dao, ObjectMapper objectMapper);


    @Override
    default void handle(Request request, Response response, Crud<T> crud, ElepyContext elepy, List<ObjectEvaluator<T>> objectEvaluators, Class<T> clazz) throws Exception {
        if (request.params("id") != null && !request.params("id").isEmpty()) {
            findOne(request, response, crud, elepy.getObjectMapper());
        } else {
            find(request, response, crud, elepy.getObjectMapper());
        }
    }
}
