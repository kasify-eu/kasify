import {ContentHeader} from "~/components/content-header";
import {Box} from "@mantine/core";
import {useState} from "react";
import {ErrorMessage} from "~/components/error-message";
import {useNavigate} from "react-router";
import {useTranslation} from "react-i18next";
import {ApiClient, createApiConfig} from "~/lib/api-client";
import {MainButton} from "~/components/main-button/main-button";
import {SecondaryButton} from "~/components/secondary-button/secondary-button";
import {
    SesHospedajesCredentialsCard,
    translateSesHospedajesError,
} from "~/components/ses-hospedajes-credentials-card";
import {showSuccessToast} from "~/lib/toast";

export default function SesHospedajesIntegrationNew() {
    const {t} = useTranslation();
    const [arrendadorCode, setArrendadorCode] = useState("");
    const [sesUsername, setSesUsername] = useState("");
    const [sesPassword, setSesPassword] = useState("");
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const navigate = useNavigate();

    const handleSubmit = async () => {
        if (!arrendadorCode.trim()) {
            setError(t("sesHospedajesNew.validation.arrendadorCode"));
            return;
        }
        if (!sesUsername.trim()) {
            setError(t("sesHospedajesNew.validation.username"));
            return;
        }
        if (!sesPassword.trim()) {
            setError(t("sesHospedajesNew.validation.password"));
            return;
        }

        setIsLoading(true);
        setError(null);

        try {
            const apiClient = ApiClient.createFromBrowser(createApiConfig());
            await apiClient.createSesHospedajesIntegration({
                arrendador_code: arrendadorCode,
                ses_username: sesUsername,
                ses_password: sesPassword,
            });
            showSuccessToast(t("sesHospedajesNew.toast.added"));
            navigate("/integrations");
            return;
        } catch (err: any) {
            console.error("Error creating integration:", err);
            setError(translateSesHospedajesError(err, t, "sesHospedajesNew.error"));
        } finally {
            setIsLoading(false);
        }
    };

    const handleCancel = () => {
        navigate("/integrations");
    };

    const breadcrumbItems = [
        {title: t("nav.integrations"), href: "/integrations"},
        {title: "SES Hospedajes", href: "#"},
    ];

    return (
        <Box>
            <ContentHeader
                breadcrumbs={breadcrumbItems}
                subtitle={t("sesHospedajesNew.subtitle")}
            />

            {error && (
                <ErrorMessage title={t("sesHospedajesNew.errorTitle")} message={error}/>
            )}

            <SesHospedajesCredentialsCard
                title={t("sesHospedajesNew.connectionDetails")}
                arrendadorCode={arrendadorCode}
                onArrendadorCodeChange={setArrendadorCode}
                arrendadorCodePlaceholder={t("sesHospedajesNew.arrendadorCode.placeholder")}
                sesUsername={sesUsername}
                onSesUsernameChange={setSesUsername}
                usernamePlaceholder={t("sesHospedajesNew.username.placeholder")}
                sesPassword={sesPassword}
                onSesPasswordChange={setSesPassword}
                passwordDescription={t("sesHospedajesNew.password.description")}
                passwordPlaceholder={t("sesHospedajesNew.password.placeholder")}
                footer={
                    <>
                        <SecondaryButton onClick={handleCancel}>
                            {t("sesHospedajesNew.cancel")}
                        </SecondaryButton>
                        <MainButton
                            onClick={handleSubmit}
                            loading={isLoading}
                            disabled={!arrendadorCode.trim() || !sesUsername.trim() || !sesPassword.trim()}
                        >
                            {t("sesHospedajesNew.connect")}
                        </MainButton>
                    </>
                }
            />
        </Box>
    );
}
