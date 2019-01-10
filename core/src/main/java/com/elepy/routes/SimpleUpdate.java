package com.elepy.routes;

import com.elepy.concepts.ObjectEvaluator;
import com.elepy.dao.Crud;
import com.elepy.di.ElepyContext;
import com.elepy.exceptions.ElepyException;
import com.elepy.utils.ClassUtils;
import spark.Request;
import spark.Response;

import java.util.List;
import java.util.Optional;

public abstract class SimpleUpdate<T> extends DefaultUpdate<T> {

    @Override
    public void handle(Request request, Response response, Crud<T> dao, ElepyContext elepy, List<ObjectEvaluator<T>> objectEvaluators, Class<T> clazz) throws Exception {
        String body = request.body();

        T item = elepy.getObjectMapper().readValue(body, clazz);

        final Optional<String> id = ClassUtils.getId(item);
        if (!id.isPresent()) {
            throw new ElepyException("This item doesn't can't be identified.");
        }

        final Optional<T> before = dao.getById(id.get());

        if (!before.isPresent()) {
            throw new ElepyException("This item doesn't exist and therefor can't be updated");
        }

        beforeUpdate(before.get(), dao, elepy);

        T updatedObjectFromRequest = updatedObjectFromRequest(before.get(),
                request,
                elepy.getObjectMapper(),
                clazz);

        final T updated =
                this.update(before.get(),
                        updatedObjectFromRequest,
                        dao,
                        objectEvaluators,
                        clazz);
        afterUpdate(before.get(), updated, dao, elepy);

    }

    public abstract void beforeUpdate(T beforeVersion, Crud<T> crud, ElepyContext elepy) throws Exception;

    public abstract void afterUpdate(T beforeVersion, T updatedVersion, Crud<T> crud, ElepyContext elepy);
}
