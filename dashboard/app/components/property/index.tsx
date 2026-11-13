import {Box, Group, Text} from "@mantine/core";
import {IconArrowRight, IconCheck, IconHome, IconMapPin} from "@tabler/icons-react";
import {useNavigate} from "react-router";
import { useTranslation } from "react-i18next";
import {isManagerOrAbove, type Property as PropertyType} from "~/types/properties";
import classes from "./index.module.css";

interface PropertyProps {
  property: PropertyType;
}

export function Property({ property }: PropertyProps) {
  const navigate = useNavigate();
  const { t } = useTranslation();

  // SES fields are manager-visibility on the API; staff/contractors can't
  // distinguish "not set up" from "hidden", so the chip is omitted for them.
  const showSesChip = isManagerOrAbove(property.role);
  const sesEnabled = !!property.ses_enabled;

  const getLocation = () => {
    const parts = [property.city, property.country].filter(Boolean);
    return parts.length > 0 ? parts.join(", ") : "No location specified";
  };

  return (
    <Box
      className={classes.propertyCard}
      role="button"
      tabIndex={0}
      onClick={() => navigate(`/properties/${property.id}`)}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          navigate(`/properties/${property.id}`);
        }
      }}
    >
      <Box className={classes.imageContainer}>
        {property.cover_image_url ? (
          <img
            src={property.cover_image_url}
            alt={property.name}
            style={{ width: "100%", height: "100%", objectFit: "cover" }}
          />
        ) : (
          <Box className={classes.imagePlaceholder}>
            <IconHome size={48} stroke={1.5} color="#94a3b8"/>
            <button
              type="button"
              className={classes.addImageLink}
              onClick={(e) => {
                e.stopPropagation();
                navigate(`/properties/${property.id}/settings`);
              }}
              onKeyDown={(e) => e.stopPropagation()}
            >
              {t("properties.addImage")}
            </button>
          </Box>
        )}
      </Box>
      <Box className={classes.cardContent}>
        <Text className={classes.propertyName}>{property.name}</Text>
        <Group gap={4} wrap="nowrap">
          <IconMapPin size={14} color="#64748b" stroke={2}/>
          <Text className={classes.propertyLocation}>{getLocation()}</Text>
        </Group>
        {showSesChip && (
          sesEnabled ? (
            <span className={`${classes.sesChip} ${classes.sesChipOn}`}>
              <IconCheck size={13} stroke={2.4}/>
              {t("properties.sesConnected")}
            </span>
          ) : (
            <button
              type="button"
              className={`${classes.sesChip} ${classes.sesChipSetup}`}
              onClick={(e) => {
                e.stopPropagation();
                navigate("/integrations");
              }}
              onKeyDown={(e) => e.stopPropagation()}
            >
              {t("properties.sesSetup")}
              <IconArrowRight size={13} stroke={2.4}/>
            </button>
          )
        )}
      </Box>
    </Box>
  );
}
