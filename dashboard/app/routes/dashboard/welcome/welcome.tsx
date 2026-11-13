import { useState } from "react";
import {
  Box,
  Container,
  Flex,
  Group,
  Paper,
  Stack,
  Text,
  TextInput,
} from "@mantine/core";
import { redirect, useLoaderData, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import {
  IconArrowRight,
  IconLogin,
  IconPlus,
  IconSparkles,
  IconUser,
  IconUserPlus,
  IconUsers,
} from "@tabler/icons-react";
import tl from "~/components/activity-timeline/index.module.css";
import {
  ApiClient,
  ApiClientError,
  createApiConfig,
  handleApiError,
} from "~/lib/api-client";
import { userContext } from "~/context";
import { ContentHeader } from "~/components/content-header";
import { LoaderError } from "~/components/loader-error";
import { MainButton } from "~/components/main-button/main-button";
import { showSuccessToast } from "~/lib/toast";
import type { Route } from "./+types/welcome";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Kasify | Welcome" }];
}

export async function loader({ request, context }: Route.LoaderArgs) {
  const user = context.get(userContext);
  try {
    const apiClient = await ApiClient.createAuthenticated(
      request,
      createApiConfig(),
    );

    const properties = await apiClient.getProperties();

    // Established users never see the welcome screen.
    if (properties.data.properties.length > 0) {
      return redirect("/timeline");
    }

    return { givenName: user?.given_name ?? null, error: null };
  } catch (error) {
    const apiError = handleApiError(error);
    return { givenName: user?.given_name ?? null, error: apiError.message };
  }
}

function translateErrorCode(
  t: ReturnType<typeof useTranslation>["t"],
  code: string | null | undefined,
  fallback: string,
): string {
  if (code) {
    const key = `welcome.error.${code}`;
    const translated = t(key);
    if (translated !== key) return translated;
  }
  return fallback || t("welcome.joinError");
}

// Static, non-interactive replica of a real ActivityTimeline row — reuses the
// timeline's own CSS module so the preview matches the live screen exactly.
function PreviewRow({
  kind,
  time,
  sub,
  icon,
  title,
  meta,
}: {
  kind: "check_in" | "check_out" | "cleaning";
  time: string;
  sub: string;
  icon: React.ReactNode;
  title: string;
  meta: React.ReactNode;
}) {
  return (
    <Box className={tl.row} data-kind={kind} style={{ cursor: "default" }}>
      <Box className={tl.time}>
        <span className={tl.timeMain}>{time}</span>
        <span className={tl.timeSub}>{sub}</span>
      </Box>
      <Box className={tl.disc} data-kind={kind}>
        {icon}
      </Box>
      <Box className={tl.body}>
        <span className={tl.title}>{title}</span>
        <Box className={tl.meta}>{meta}</Box>
      </Box>
    </Box>
  );
}

function PreviewPropertyBlock({
  name,
  children,
}: {
  name: string;
  children: React.ReactNode;
}) {
  return (
    <Box className={tl.pblock}>
      <Box className={tl.pblockHead}>
        <Text component="span" className={tl.pblockHeadName}>
          {name}
        </Text>
      </Box>
      <Box className={tl.pblockBody}>{children}</Box>
    </Box>
  );
}

export default function Welcome() {
  const { givenName, error } = useLoaderData<typeof loader>();
  const { t } = useTranslation();
  const navigate = useNavigate();

  const [code, setCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [joinError, setJoinError] = useState<string | null>(null);

  const headerTitle = givenName
    ? t("welcome.headerTitleNamed", { name: givenName })
    : t("welcome.headerTitle");

  if (error) {
    return <LoaderError title={headerTitle} />;
  }

  const handleJoin = async () => {
    if (!code.trim()) {
      setJoinError(t("welcome.codeError"));
      return;
    }

    setLoading(true);
    setJoinError(null);

    try {
      const apiClient = ApiClient.createFromBrowser(createApiConfig());
      const response = await apiClient.acceptInvitation(code.trim());
      showSuccessToast(
        t("propertyJoinModal.successSubtitlePlain", {
          propertyName: response.data.propertyName,
        }),
      );
      navigate("/timeline");
    } catch (err) {
      const errorCode = err instanceof ApiClientError ? err.code : null;
      const fallback = err instanceof Error ? err.message : "";
      setJoinError(translateErrorCode(t, errorCode, fallback));
      setLoading(false);
    }
  };

  return (
    <Container fluid={true}>
      <ContentHeader
        title={headerTitle}
        subtitle={t("welcome.headerSubtitle")}
      />

      <Paper withBorder radius="lg" p={{ base: "lg", sm: 36 }} maw={1600}>
        <Flex
          direction={{ base: "column", lg: "row" }}
          gap={{ base: "xl", md: 32 }}
          align="stretch"
        >
          {/* Left — hero + tucked invite */}
          <Stack gap="md" style={{ flex: 1, minWidth: 0 }}>
            <Group gap={6}>
              <Box
                w={6}
                h={6}
                bg="green.6"
                style={{ borderRadius: "50%" }}
              />
              <Text
                ff="monospace"
                fw={700}
                size="xs"
                c="dimmed"
                tt="uppercase"
                style={{ letterSpacing: "0.14em" }}
              >
                {t("welcome.heroEyebrow")}
              </Text>
            </Group>

            <Text
              fz={{ base: 26, sm: 32 }}
              fw={700}
              style={{ letterSpacing: "-0.025em", lineHeight: 1.1 }}
            >
              {t("welcome.heroTitle")}
            </Text>

            <Text c="dimmed" size="sm" maw="80ch" style={{ lineHeight: 1.55 }}>
              {t("welcome.heroLead")}
            </Text>

            <Box mt={4}>
              <MainButton
                size="lg"
                leftSection={<IconPlus size={16} />}
                onClick={() => navigate("/properties/new")}
              >
                {t("welcome.heroCta")}
              </MainButton>
            </Box>

            <Paper
              mt="sm"
              p="md"
              radius="md"
              bg="gray.0"
              style={{ border: "1px dashed var(--mantine-color-gray-3)" }}
              maw={720}
            >
              <Stack gap="sm">
                <Box>
                  <Group gap={8}>
                    <IconUserPlus
                      size={14}
                      color="var(--mantine-color-gray-7)"
                    />
                    <Text size="sm" fw={600}>
                      {t("welcome.inviteTitle")}
                    </Text>
                  </Group>
                  <Text size="xs" c="dimmed" mt={2}>
                    {t("welcome.inviteSubtitle")}
                  </Text>
                </Box>
                <TextInput
                  value={code}
                  onChange={(e) =>
                    setCode(e.currentTarget.value.trim())
                  }
                  placeholder={t("welcome.invitePlaceholder")}
                  disabled={loading}
                  maw={360}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") handleJoin();
                  }}
                  styles={{
                    input: {
                      fontFamily: "monospace",
                      letterSpacing: "0.08em",
                    },
                  }}
                />
                <Box>
                  <MainButton
                    onClick={handleJoin}
                    loading={loading}
                    rightSection={<IconArrowRight size={14} />}
                  >
                    {t("welcome.inviteButton")}
                  </MainButton>
                </Box>
              </Stack>
              {joinError && (
                <Text c="red" size="sm" mt="xs">
                  {joinError}
                </Text>
              )}
            </Paper>
          </Stack>

          {/* Right — decorative preview mirroring the real timeline */}
          <Paper
            radius="md"
            p="md"
            w={{ base: "100%", md: 380 }}
            style={{
              flexShrink: 0,
              background: "#f8fafc",
              border: "1px solid var(--mantine-color-gray-2)",
            }}
          >
            <Text size="xs" fw={600} c="dimmed" mb="sm">
              {t("welcome.previewHeading")}
            </Text>
            <Box className={tl.root} style={{ paddingBottom: 0 }}>
              <Group
                className={tl.day}
                gap="md"
                wrap="nowrap"
                align="baseline"
                style={{ position: "static" }}
              >
                <Group gap={6} align="baseline" wrap="nowrap">
                  <Text component="span" className={tl.dayRel}>
                    {t("timeline.inDays", { count: 3 })}
                  </Text>
                  <Text component="span" className={tl.dayDate}>
                    <span className={tl.dayDateWkd}>Monday</span>, 21 May
                  </Text>
                </Group>
                <Box className={tl.daySpacer} />
              </Group>

              <Stack gap={10} className={tl.sectionBody}>
                <PreviewPropertyBlock name="Lake House">
                  <PreviewRow
                    kind="cleaning"
                    time="12:30"
                    sub="– 14:00"
                    icon={<IconSparkles size={18} stroke={2.25} />}
                    title={t("timeline.cleaning")}
                    meta={
                      <span className={tl.mk}>
                        <IconUser size={12} />
                        <span className={tl.mkVal}>Lucía</span>
                      </span>
                    }
                  />
                  <PreviewRow
                    kind="check_in"
                    time="14:30"
                    sub={t("timeline.checkIn")}
                    icon={<IconLogin size={18} stroke={2.25} />}
                    title="María García"
                    meta={
                      <>
                        <span className={tl.mk}>
                          <IconUsers size={12} />
                          <span className={tl.mkVal}>
                            {t("timeline.adults", { count: 2 })}
                          </span>
                        </span>
                        <span className={tl.mkSep} />
                        <span className={tl.channel}>
                          <span
                            className={tl.channelDot}
                            data-brand="airbnb"
                          />
                          {t("timeline.channelAirbnb")}
                        </span>
                        <span className={tl.mkSep} />
                        <span className={tl.mk}>
                          <span
                            className={tl.statusDot}
                            data-state="pending"
                          />
                          <span className={tl.mkVal}>
                            {t("timeline.checkinPending")}
                          </span>
                        </span>
                      </>
                    }
                  />
                </PreviewPropertyBlock>
              </Stack>
            </Box>
          </Paper>
        </Flex>
      </Paper>
    </Container>
  );
}
