import {Group, Text} from "@mantine/core";

interface HeaderSectionProps {
  label: string;
  size?: "xs" | "sm" | "md" | "lg" | "xl";
  fontWeight?: number;
  textColor?: string;
  lineColor?: string;
  lineHeight?: number;
  paddingLeft?: string;
  marginBottom?: string;
}

export function HeaderSection({
  label,
                                size = "lg",
  fontWeight = 700,
  textColor = "#374151",
  lineColor = "#E5E7EB",
  lineHeight = 1,
  paddingLeft = "4",
  marginBottom = "lg",
}: HeaderSectionProps) {
  return (
    <Group gap="md" mb={marginBottom} wrap="nowrap" align="center">
      <Text
        size={size}
        pl={paddingLeft}
        fw={fontWeight}
        c={textColor}
        style={{ letterSpacing: "0.3px", whiteSpace: "nowrap" }}
      >
        {label}
      </Text>
    </Group>
  );
}
