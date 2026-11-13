import {ApiClient, createApiConfig, handleApiError} from "~/lib/api-client";
import {Form, Link, redirect, useActionData, useLoaderData, useNavigation} from "react-router";
import {Alert, Box, Button, Card, Group, Stack} from "@mantine/core";
import {IconArrowLeft, IconPlus} from "@tabler/icons-react";
import type {Route} from "./+types/new";
import {ContentHeader} from "~/components/content-header";
import {MainButton} from "~/components/main-button/main-button";
import {useState} from "react";
import type {TaskType} from "~/types/tasks";
import {useTranslation} from "react-i18next";
import {
    emptyTaskFormValues,
    TaskFormFields,
    type TaskFormValues,
} from "~/components/task-form-fields";

export async function loader({request}: Route.LoaderArgs) {
    try {
        const apiClient = await ApiClient.createAuthenticated(
            request,
            createApiConfig(),
        );
        const propertiesResponse = await apiClient.getProperties();

        return {
            properties: propertiesResponse.data.properties,
            error: null,
        };
    } catch (error) {
        const apiError = handleApiError(error);
        return {properties: [], error: apiError.message};
    }
}

export async function action({request}: Route.ActionArgs) {
    try {
        const formData = await request.formData();

        const propertyId = formData.get("propertyId") as string;
        const taskType = formData.get("taskType") as TaskType;
        const assignedTo = formData.get("assignedTo") as string | null;
        const fromValue = formData.get("from") as string;
        const toValue = formData.get("to") as string;
        const notes = formData.get("notes") as string | null;

        if (!propertyId || !taskType || !fromValue || !toValue) {
            return {error: "tasksNew.validateRequired", success: false};
        }

        if (!assignedTo) {
            return {error: "tasksNew.validateAssignee", success: false};
        }

        const apiClient = await ApiClient.createAuthenticated(
            request,
            createApiConfig(),
        );

        await apiClient.createTask({
            property_id: propertyId,
            task_type: taskType,
            assigned_to: assignedTo,
            from: pickerToIso(fromValue),
            to: pickerToIso(toValue),
            notes: notes || undefined,
        });

        return redirect(`/tasks`);
    } catch (error) {
        const apiError = handleApiError(error);
        return {error: apiError.message, success: false};
    }
}

// "YYYY-MM-DD HH:mm:ss" (Mantine) → "YYYY-MM-DDTHH:mm:ss.000Z"
function pickerToIso(value: string): string {
    const trimmed = value.length === 16 ? `${value}:00` : value;
    return `${trimmed.replace(" ", "T")}.000Z`;
}

export default function NewTask() {
    const {properties, error: loaderError} = useLoaderData<typeof loader>();
    const actionData = useActionData<typeof action>();
    const navigation = useNavigation();
    const isSubmitting = navigation.state === "submitting";
    const {t} = useTranslation();

    const [values, setValues] = useState<TaskFormValues>(() =>
        emptyTaskFormValues(),
    );
    const handleChange = (patch: Partial<TaskFormValues>) =>
        setValues((v) => ({...v, ...patch}));

    const breadcrumbItems = [
        {title: t("nav.tasks"), href: "/tasks"},
        {title: t("tasksNew.breadcrumb"), href: "#"},
    ];

    if (loaderError) {
        return (
            <Box>
                <Link to="/tasks">
                    <Button variant="light" leftSection={<IconArrowLeft size={16}/>}>
                        {t("tasksNew.backToTasks")}
                    </Button>
                </Link>
                <Alert color="red" title={t("tasksNew.errorTitle")} mt="md">
                    {loaderError}
                </Alert>
            </Box>
        );
    }

    const propertyOptions = properties.map((p) => ({id: p.id, name: p.name}));

    return (
        <Box>
            <ContentHeader
                breadcrumbs={breadcrumbItems}
                subtitle={t("tasksNew.subtitle")}
            />

            <Card withBorder p="xl" maw={600}>
                <Form method="post">
                    <Stack gap="lg">
                        {actionData?.error && (
                            <Alert color="red" title={t("tasksNew.errorTitle")}>
                                {t(actionData.error, {defaultValue: actionData.error})}
                            </Alert>
                        )}

                        <TaskFormFields
                            properties={propertyOptions}
                            values={values}
                            onChange={handleChange}
                        />

                        <Group justify="flex-end" mt="md">
                            <Link to="/tasks">
                                <Button variant="light">{t("tasksNew.cancel")}</Button>
                            </Link>
                            <MainButton
                                type="submit"
                                loading={isSubmitting}
                                leftSection={<IconPlus size={16}/>}
                            >
                                {t("tasksNew.create")}
                            </MainButton>
                        </Group>
                    </Stack>
                </Form>
            </Card>
        </Box>
    );
}
