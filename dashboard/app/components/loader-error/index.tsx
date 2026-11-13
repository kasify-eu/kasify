import { Container } from "@mantine/core";
import { useTranslation } from "react-i18next";
import { ContentHeader } from "~/components/content-header";
import { ErrorMessage } from "~/components/error-message";

type LoaderErrorProps = {
  title: string;
};

export function LoaderError({ title }: LoaderErrorProps) {
  const { t } = useTranslation();

  return (
    <Container fluid={true}>
      <ContentHeader title={title} />
      <ErrorMessage
        title={t("common.loaderErrorTitle")}
        message={t("common.loaderErrorMessage")}
      />
    </Container>
  );
}
