package com.elepy.utils;

import com.elepy.annotations.*;
import com.elepy.auth.Permissions;
import com.elepy.describers.Model;
import com.elepy.describers.Property;
import com.elepy.describers.props.*;
import com.elepy.exceptions.ElepyConfigException;
import com.elepy.http.ActionType;
import com.elepy.http.HttpAction;
import com.elepy.http.HttpMethod;
import com.elepy.models.FieldType;

import javax.persistence.Column;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModelUtils {

    public static List<Property> describeClass(Class cls) {
        return Stream.concat(
                Stream.of(cls.getDeclaredFields())
                        .map(ModelUtils::describeFieldOrMethod),
                Stream.of(cls.getDeclaredMethods())
                        .filter(method -> method.isAnnotationPresent(Generated.class))
                        .map(ModelUtils::describeFieldOrMethod)
        ).sorted(Comparator.naturalOrder()).collect(Collectors.toList());


    }

    public static Property describeFieldOrMethod(AccessibleObject accessibleObject) {
        Property property = new Property();

        final boolean idField;

        if (accessibleObject instanceof Field) {
            idField = ReflectionUtils.getIdField(((Field) accessibleObject).getDeclaringClass()).map(field1 -> field1.getName().equals(((Field) accessibleObject).getName())).orElse(false);
        } else {
            idField = false;
        }

        final Column column = accessibleObject.getAnnotation(Column.class);
        final Importance importance = accessibleObject.getAnnotation(Importance.class);

        property.setHiddenFromCMS(accessibleObject.isAnnotationPresent(Hidden.class));
        property.setName(ReflectionUtils.getPropertyName(accessibleObject));
        property.setPrettyName(ReflectionUtils.getPrettyName(accessibleObject));
        property.setRequired(accessibleObject.getAnnotation(Required.class) != null);
        property.setEditable(!idField && (!accessibleObject.isAnnotationPresent(Uneditable.class) || (column != null && !column.updatable())));
        property.setImportance(importance == null ? 0 : importance.value());
        property.setUnique(accessibleObject.isAnnotationPresent(Unique.class) || (column != null && column.unique()));
        property.setGenerated(accessibleObject.isAnnotationPresent(Generated.class) || (idField && !accessibleObject.isAnnotationPresent(Identifier.class)) || (idField && accessibleObject.isAnnotationPresent(Identifier.class) && accessibleObject.getAnnotation(Identifier.class).generated()));

        property.setSearchable(accessibleObject.isAnnotationPresent(Searchable.class));
        property.config(mapFieldTypeInformation(accessibleObject));
        return property;
    }

    public static <T> Model<T> createModelFromClass(Class<T> classType) {
        var model = new Model<T>();
        final RestModel restModel = classType.getAnnotation(RestModel.class);

        model.setSlug(restModel.slug());
        model.setName(restModel.name());
        model.setJavaClass(classType);
        model.setDefaultSortDirection(restModel.defaultSortDirection());
        model.setProperties(ModelUtils.describeClass(classType));


        setupDefaultActions(model);
        setupIdField(model);
        setupActions(model);

        final String toGet = restModel.defaultSortField();
        final String idField = model.getIdField();
        model.setDefaultSortField(StringUtils.getOrDefault(toGet, idField));

        return model;
    }

    private static <T> void setupActions(Model<T> model) {

        model.setActions(Stream.of(model.getJavaClass().getAnnotationsByType(Action.class))
                .map(actionAnnotation -> actionToHttpAction(model.getSlug(), actionAnnotation)).collect(Collectors.toList()));

    }

    public static HttpAction actionToHttpAction(String modelSlug, Action actionAnnotation) {
        final String multiSlug = modelSlug + "/actions" + (actionAnnotation.slug().isEmpty() ? "/" + StringUtils.slugify(actionAnnotation.name()) : actionAnnotation.slug());
        return HttpAction.of(actionAnnotation.name(), multiSlug, actionAnnotation.requiredPermissions(), actionAnnotation.method(), actionAnnotation.actionType());
    }

    private static void setupIdField(Model<?> model) {

        var cls = model.getJavaClass();
        Field field = ReflectionUtils.getIdField(cls).orElseThrow(() -> new ElepyConfigException(cls.getName() + " doesn't have a valid identifying field, please annotate a String/Long/Int field with @Identifier"));

        if (!Arrays.asList(Long.class, String.class, Integer.class).contains(org.apache.commons.lang3.ClassUtils.primitivesToWrappers(field.getType())[0])) {
            throw new ElepyConfigException(String.format("The id field '%s' is not a Long, String or Int", field.getName()));
        }

        model.setIdField(ReflectionUtils.getPropertyName(field));
    }

    private static void setupDefaultActions(Model<?> model) {

        var createPermissions = Optional
                .ofNullable(model.getJavaClass().getAnnotation(Create.class))
                .map(Create::requiredPermissions)
                .orElse(Permissions.DEFAULT);

        var updatePermissions = Optional
                .ofNullable(model.getJavaClass().getAnnotation(Update.class))
                .map(Update::requiredPermissions)
                .orElse(Permissions.DEFAULT);

        var deletePermissions = Optional
                .ofNullable(model.getJavaClass().getAnnotation(Delete.class))
                .map(Delete::requiredPermissions)
                .orElse(Permissions.DEFAULT);

        var findPermissions = Optional
                .ofNullable(model.getJavaClass().getAnnotation(Find.class))
                .map(Find::requiredPermissions)
                .orElse(Permissions.NONE);


        model.setFindOneAction(HttpAction.of("Find One", model.getSlug() + "/:id", findPermissions, HttpMethod.GET, ActionType.SINGLE));
        model.setFindManyAction(HttpAction.of("Find Many", model.getSlug(), findPermissions, HttpMethod.GET, ActionType.MULTIPLE));
        model.setUpdateAction(HttpAction.of("Update", model.getSlug() + "/:id", updatePermissions, HttpMethod.PUT, ActionType.SINGLE));
        model.setDeleteAction(HttpAction.of("Delete", model.getSlug() + "/:id", deletePermissions, HttpMethod.DELETE, ActionType.SINGLE));
        model.setCreateAction(HttpAction.of("Create", model.getSlug(), createPermissions, HttpMethod.POST, ActionType.MULTIPLE));

    }

    public static PropertyConfig mapFieldTypeInformation(AccessibleObject field) {
        FieldType fieldType = FieldType.guessType(field);

        switch (fieldType) {
            case TEXT:
                return TextPropertyConfig.of(field);
            case DATE:
                return DatePropertyConfig.of(field);
            case NUMBER:
                return NumberPropertyConfig.of(field);
            case ENUM:
                return EnumPropertyConfig.of(field);
            case OBJECT:
                return ObjectPropertyConfig.of(field);
            case BOOLEAN:
                return BooleanPropertyConfig.of(field);
            case ARRAY:
                return ArrayPropertyConfig.of(field);
            default:
                throw new ElepyConfigException(String.format("%s is not supported", fieldType.name()));

        }

    }

}
