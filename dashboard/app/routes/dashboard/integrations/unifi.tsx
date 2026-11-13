import {ContentHeader} from "~/components/content-header";
import {Box, Group, PasswordInput, Stack, Text, TextInput,} from "@mantine/core";
import {useState} from "react";
import {ErrorMessage} from "~/components/error-message";
import {useNavigate} from "react-router";
import {ApiClient, createApiConfig} from "~/lib/api-client";
import {MainButton} from "~/components/main-button/main-button";
import {SecondaryButton} from "~/components/secondary-button/secondary-button";

type UnifiIntegrationResponse = {
    id: string;
};

type ErrorResponse = {
    code: string;
    message: string;
    error_id?: string;
    timestamp: string;
};

export default function UnifiIntegration() {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [consoleUrl, setConsoleUrl] = useState("");
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const navigate = useNavigate();

    const handleSubmit = async () => {
        if (!consoleUrl.trim()) {
            setError("Please enter your Unifi console URL");
            return;
        }
        if (!username.trim()) {
            setError("Please enter your Unifi username");
            return;
        }
        if (!password.trim()) {
            setError("Please enter your Unifi password");
            return;
        }

        setIsLoading(true);
        setError(null);

        try {
            const apiClient = ApiClient.createFromBrowser(createApiConfig());
            const response = await apiClient.post<UnifiIntegrationResponse>("/integrations/unifi", {
                unifi_username: username,
                unifi_password: password,
                unifi_console_url: consoleUrl,
            });
            navigate(`/integrations/unifi/${response.data.id}`);
        } catch (err: any) {
            if (err?.data && typeof err.data === "object") {
                const errorData = err.data as ErrorResponse;
                setError(errorData.message || "Failed to add Unifi integration");
            } else if (err instanceof Error) {
                setError(err.message);
            } else {
                setError("Failed to add Unifi integration");
            }
        } finally {
            setIsLoading(false);
        }
    };

    const handleCancel = () => {
        navigate("/integrations");
    };

    const breadcrumbItems = [
        {title: "Integrations", href: "/integrations"},
        {title: "Unifi Access", href: "#"},
    ];

    return (
        <Box>
            <ContentHeader
                breadcrumbs={breadcrumbItems}
                subtitle="Connect your Unifi Access account to manage smart locks"
            />

            {error && (
                <ErrorMessage title="Connection Failed" message={error}/>
            )}

            <Box
                style={{
                    backgroundColor: "white",
                    borderRadius: 16,
                    padding: 32,
                    maxWidth: 600,
                }}
            >
                <Stack gap="lg">
                    <Text fw={600} size="lg">
                        Connection Details
                    </Text>

                    <TextInput
                        label="Console URL"
                        description="Your Unifi Access console URL (e.g., https://unifi.ui.com)"
                        placeholder="https://unifi.ui.com"
                        value={consoleUrl}
                        onChange={(e) => setConsoleUrl(e.currentTarget.value)}
                        required
                    />

                    <TextInput
                        label="Username"
                        description="Your Unifi account username or email"
                        placeholder="Enter your Unifi username"
                        value={username}
                        onChange={(e) => setUsername(e.currentTarget.value)}
                        required
                    />

                    <PasswordInput
                        label="Password"
                        description="Your Unifi account password"
                        placeholder="Enter your Unifi password"
                        value={password}
                        onChange={(e) => setPassword(e.currentTarget.value)}
                        required
                    />

                    <Group justify="flex-start" mt="xl">
                        <SecondaryButton onClick={handleCancel}>
                            Cancel
                        </SecondaryButton>
                        <MainButton
                            onClick={handleSubmit}
                            loading={isLoading}
                            disabled={!consoleUrl.trim() || !username.trim() || !password.trim()}
                        >
                            Connect
                        </MainButton>
                    </Group>
                </Stack>
            </Box>
        </Box>
    );
}
