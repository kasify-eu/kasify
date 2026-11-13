import { Stack, Text, Title, Image } from "@mantine/core";
import { useNavigate } from "react-router";
import { MainButton } from "~/components/main-button/main-button";

interface EmptyStateProps {
  title?: string;
  subtitle?: string;
  image?: string;
  button?: {
    name: string;
    url: string;
  };
}

export function EmptyState({
  title,
  subtitle,
  image,
  button,
}: EmptyStateProps) {
  const navigate = useNavigate();

  return (
    <Stack align="center" justify="center" gap="4px" py={{ base: "sm", sm: "xl" }} mt={{ base: "sm", sm: "xl" }}>
      {title && (
        <Title order={3} ta="center">
          {title}
        </Title>
      )}

      {subtitle && (
        <Text c="dimmed" size="sm" ta="center" maw={600}>
          {subtitle}
        </Text>
      )}

      {image && (
        <Image src={image} alt="Empty state" maw={{ base: 80, sm: 100 }} mx="auto" mt={{ base: "xl", sm: "xl" }} mb={{ base: "md", sm: "xl" }} />
      )}

      {button && (
        <MainButton onClick={() => navigate(button.url)}>
          {button.name}
        </MainButton>
      )}
    </Stack>
  );
}
