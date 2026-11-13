import { ActionIcon, Box, Text } from "@mantine/core";
import { IconAlertCircle, IconX } from "@tabler/icons-react";
import type { ReactNode } from "react";

type ErrorMessageProps = {
  title?: string;
  message: string;
  children?: ReactNode;
  mb?: string | number;
};

export function ErrorMessage({ title, message, children, mb = "lg" }: ErrorMessageProps) {
  return (
    <Box
      mb={mb}
      style={{
        backgroundColor: "rgba(255, 218, 214, 0.5)",
        borderLeft: "4px solid #ba1a1a",
        borderRadius: 8,
        padding: "20px 24px",
        display: "flex",
        alignItems: "flex-start",
        gap: 16,
        boxShadow: "0 1px 3px rgba(0,0,0,0.06)",
      }}
    >
      <Box
        style={{
          backgroundColor: "#ffdad6",
          borderRadius: 9999,
          padding: 8,
          color: "#93000a",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          flexShrink: 0,
          marginRight: 8,
        }}
      >
        <IconAlertCircle size={24} />
      </Box>
      <Box style={{ flex: 1 }}>
        {title && (
          <Text fw={700} size="md" mb={4} style={{ color: "#93000a", lineHeight: 1 }}>
            {title}
          </Text>
        )}
        <Text size="sm" mt={title ? "sm" : undefined} style={{ color: "#45464d" }}>
          {message}
        </Text>
        {children && <Box mt="md">{children}</Box>}
      </Box>
    </Box>
  );
}
