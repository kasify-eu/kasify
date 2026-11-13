import { Card, Container, Group, Select, SimpleGrid, Stack, TextInput } from "@mantine/core";
import type { Route } from "./+types/index";
import { ContentHeader } from "~/components/content-header";
import { MainButton } from "~/components/main-button/main-button";
import { IconCheck } from "@tabler/icons-react";
import { ApiClient, createApiConfig, handleApiError } from "~/lib/api-client";
import {
  data,
  Form,
  useActionData,
  useLoaderData,
  useNavigation,
  useRevalidator,
} from "react-router";
import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { showErrorToast, showSuccessToast } from "~/lib/toast";
import { ApiTokensCard } from "~/components/api-tokens-card";
import { TelegramCard } from "~/components/telegram-card";
import type { ApiToken } from "~/types/api-tokens";
import type { Locale } from "~/types/account";
import type { TelegramStatusResponse } from "~/types/telegram";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Kasify | Account" }];
}

export async function loader({ request }: Route.LoaderArgs) {
    try {
        const apiClient = await ApiClient.createAuthenticated(request, createApiConfig());
        const [accountResponse, tokensResponse] = await Promise.all([
            apiClient.getAccount(),
            apiClient.getApiTokens(),
        ]);
        // Telegram status is a secondary concern — fetch it independently so a failure
        // here can't take down the account page; fall back to "not connected".
        let telegram: TelegramStatusResponse = { status: "none" };
        try {
            telegram = (await apiClient.getTelegramStatus()).data;
        } catch {
            // ignore — keep the "none" fallback
        }
        return {
            account: accountResponse.data,
            apiTokens: tokensResponse.data,
            telegram,
            error: null,
        };
    } catch (error) {
        const apiError = handleApiError(error);
        return {
            account: null,
            apiTokens: [] as ApiToken[],
            telegram: { status: "none" } as TelegramStatusResponse,
            error: apiError.message,
        };
    }
}

export async function action({ request }: Route.ActionArgs) {
    try {
        const formData = await request.formData();
        const name = formData.get("name") as string;
        const lastName = formData.get("last_name") as string;
        const rawLocale = formData.get("locale");
        const locale: Locale | undefined =
            rawLocale === "en" || rawLocale === "es" ? rawLocale : undefined;

        if (!name || !lastName) {
            return data({ error: "account.errorRequired", success: false, locale: null });
        }

        const apiClient = await ApiClient.createAuthenticated(request, createApiConfig());
        await apiClient.updateAccount({ name, last_name: lastName, locale });

        // Persist the chosen language as the UI cookie too, so the display language matches the
        // saved preference across reloads/SSR (mirrors /api/set-locale). The client also switches
        // instantly via i18n.changeLanguage below.
        const headers: Record<string, string> = {};
        if (locale) {
            const secure = process.env.NODE_ENV === "production" ? " Secure;" : "";
            headers["Set-Cookie"] =
                `kasify_locale=${locale}; Path=/; Max-Age=31536000; SameSite=Lax;${secure}`;
        }

        return data({ error: null, success: true, locale: locale ?? null }, { headers });
    } catch (error) {
        const apiError = handleApiError(error);
        return data({ error: apiError.message, success: false, locale: null });
    }
}

export default function AccountDetails() {
    const { account, apiTokens, telegram, error: loaderError } = useLoaderData<typeof loader>();
    const actionData = useActionData<typeof action>();
    const navigation = useNavigation();
    const revalidator = useRevalidator();
    const { t, i18n } = useTranslation();
    const isSubmitting = navigation.state === "submitting";

    useEffect(() => {
        if (actionData?.success) {
            showSuccessToast(t("account.saveSuccess"));
            // Reflect the newly saved language in the UI immediately (the cookie was set server-side).
            if (actionData.locale && actionData.locale !== i18n.language) {
                i18n.changeLanguage(actionData.locale);
            }
        } else if (actionData?.error) {
            showErrorToast(t(actionData.error));
        }
    }, [actionData]);

    useEffect(() => {
        if (loaderError) {
            showErrorToast(loaderError);
        }
    }, [loaderError]);

    return (
        <Container fluid={true}>
            <ContentHeader
                title={t("account.title")}
                subtitle={t("account.subtitle")}
            />

            <Card withBorder p="xl" maw={900} radius="lg">
                <Form method="post">
                    <Stack gap="lg">

                        <SimpleGrid cols={{ base: 1, sm: 2 }}>
                            <TextInput
                                label={t("account.firstName")}
                                name="name"
                                defaultValue={account?.name ?? ""}
                                required
                            />
                            <TextInput
                                label={t("account.lastName")}
                                name="last_name"
                                defaultValue={account?.last_name ?? ""}
                                required
                            />
                        </SimpleGrid>

                        <TextInput
                            label={t("account.email")}
                            value={account?.email ?? ""}
                            disabled
                            description={t("account.emailDescription")}
                        />

                        <SimpleGrid cols={{ base: 1, sm: 2 }}>
                            <Select
                                label={t("account.language")}
                                description={t("account.languageDescription")}
                                name="locale"
                                defaultValue={account?.locale ?? "es"}
                                allowDeselect={false}
                                data={[
                                    { value: "en", label: "English" },
                                    { value: "es", label: "Español" },
                                ]}
                            />
                        </SimpleGrid>
                        <Group justify="flex-end" mt="md">
                            <MainButton
                                type="submit"
                                loading={isSubmitting}
                                leftSection={<IconCheck size={16} />}
                            >
                                {t("account.save")}
                            </MainButton>
                        </Group>
                    </Stack>
                </Form>
            </Card>

            <TelegramCard
                status={telegram}
                onChanged={() => revalidator.revalidate()}
            />

            <ApiTokensCard
                tokens={apiTokens}
                onChanged={() => revalidator.revalidate()}
            />
        </Container>
    );
}
