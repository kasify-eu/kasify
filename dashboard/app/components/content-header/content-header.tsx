import {Box, Group, ActionIcon, Text, Stack, Breadcrumbs, Anchor} from "@mantine/core";
import { IconArrowLeft, IconChevronRight } from "@tabler/icons-react";
import { Link } from "react-router";
import classes from "./index.module.css";

interface BreadcrumbItem {
  title: string;
  href: string;
}

interface ContentHeaderProps {
  title?: string;
  subtitle?: string;
  right?: React.ReactNode;
  backLink?: string;
  breadcrumbs?: BreadcrumbItem[];
  marginBottom?: number | string;
}

export function ContentHeader({
  title,
  subtitle,
  right,
  backLink,
  breadcrumbs,
  marginBottom = 20,
}: ContentHeaderProps) {
  return (
    <Box className={classes.rootContentHeader} style={{ marginBottom }}>
      <Group justify="space-between" align="flex-start" w="100%" wrap="nowrap">
        <Group gap="md" align="flex-start" style={{ minWidth: 0 }}>
          {backLink && (
            <Link to={backLink}>
              <ActionIcon
                radius="xl"
                variant="subtle"
                size="lg"
                color="gray"
                mt={2}
              >
                <IconArrowLeft size={20} />
              </ActionIcon>
            </Link>
          )}
          <Stack gap={8} style={{ minWidth: 0 }}>
            <Breadcrumbs
              separator={<IconChevronRight size={14} color="#94A3B8" />}
              className={classes.breadcrumbs}
            >
              {(breadcrumbs ?? [{ title: title ?? "", href: "#" }]).map((item, index, arr) => (
                index < arr.length - 1 ? (
                  <Anchor
                    key={item.title}
                    component={Link}
                    to={item.href}
                    c="#64748B"
                    size="lg"
                    style={{ textDecoration: "none" }}
                  >
                    {item.title}
                  </Anchor>
                ) : (
                  <Text key={item.title} fw={700} size="lg" c="#0F172A">
                    {item.title}
                  </Text>
                )
              ))}
            </Breadcrumbs>
            {subtitle && (
              <Text c="dimmed" size="sm" style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                {subtitle}
              </Text>
            )}
          </Stack>
        </Group>
        {right && <Box style={{ flexShrink: 0 }}>{right}</Box>}
      </Group>
    </Box>
  );
}
