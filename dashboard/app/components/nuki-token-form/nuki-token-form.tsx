import {Box, Group, Stack, Text, TextInput} from "@mantine/core";
import {useState} from "react";
import {ErrorMessage} from "~/components/error-message";
import {MainButton} from "~/components/main-button/main-button";
import {SecondaryButton} from "~/components/secondary-button/secondary-button";

interface NukiTokenFormProps {
  onSubmit: (token: string) => Promise<void>;
  onCancel: () => void;
}

export function NukiTokenForm({onSubmit, onCancel}: NukiTokenFormProps) {
  const [nukiToken, setNukiToken] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async () => {
    if (!nukiToken.trim()) {
      setError("Please enter your Nuki API Token");
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      await onSubmit(nukiToken);
    } catch {
      setError("We were unable to establish a connection with your Nuki account. Please check your token and try again.");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Stack style={{width: 600, flexShrink: 0}}>
      {error && (
        <ErrorMessage
          title="Connection Failed"
          message={error}
        />
      )}

      <Box
        style={{
          backgroundColor: "white",
          borderRadius: 16,
          padding: 32,
          minWidth: 320,
          flexShrink: 0,
        }}
      >
        <Stack gap="lg">
          <Text fw={600} size="lg">
            Connection Details
          </Text>

          <TextInput
            label="API Token"
            description="Available in web.nuki.io under Settings > API Token"
            placeholder="Enter your Nuki API Token"
            value={nukiToken}
            onChange={(e) => setNukiToken(e.currentTarget.value)}
            required
          />

          <Group justify="flex-start" mt="xl">
            <SecondaryButton onClick={onCancel}>
              Cancel
            </SecondaryButton>
            <MainButton
              onClick={handleSubmit}
              loading={isLoading}
              disabled={!nukiToken.trim()}
            >
              Connect
            </MainButton>
          </Group>
        </Stack>
      </Box>
    </Stack>
  );
}
