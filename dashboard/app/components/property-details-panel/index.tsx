import type { ReactNode } from "react";
import { Grid } from "@mantine/core";
import { TextLabel } from "~/components/text-label";
import { DetailsDrawer } from "~/components/details-drawer/details-drawer";
import type { AdminProperty } from "~/types/admin-lists";
import classes from "~/components/details-drawer/details-drawer.module.css";
import { PropertyPanelHeader } from "./PropertyPanelHeader";

interface PropertyDetailsPanelProps {
  property: AdminProperty | null;
  opened: boolean;
  onClose: () => void;
}

function formatLongDate(dateStr: string | null): string {
  if (!dateStr) return "—";
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return "—";
  return d.toLocaleDateString("en-US", {
    weekday: "short",
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

function yesNoUnknown(value: boolean | null): string {
  if (value === null) return "Unknown";
  return value ? "Yes" : "No";
}

interface SectionProps {
  num: string;
  label: string;
  children: ReactNode;
}

function Section({ num, label, children }: SectionProps) {
  return (
    <div className={classes.section}>
      <div className={classes.sectionHead}>
        <div className={classes.sectionLabel}>
          <span className={classes.num}>{num}</span>
          {label}
        </div>
      </div>
      {children}
    </div>
  );
}

export function PropertyDetailsPanel({
  property,
  opened,
  onClose,
}: PropertyDetailsPanelProps) {
  const ownerFullName = property
    ? `${property.owner.name} ${property.owner.last_name}`.trim()
    : "";
  const calendars = property?.calendars ?? [];

  return (
    <DetailsDrawer
      opened={opened}
      onClose={onClose}
      header={property && <PropertyPanelHeader property={property} onClose={onClose} />}
    >
      {property && (
        <>
          <Section num="01" label="Location">
            <Grid gutter={{ base: 14, sm: 20 }}>
              <Grid.Col span={12}>
                <TextLabel label="Address" text={property.address || "—"} />
              </Grid.Col>
              <Grid.Col span={6}>
                <TextLabel label="City" text={property.city || "—"} />
              </Grid.Col>
              <Grid.Col span={6}>
                <TextLabel label="ZIP code" text={property.zip_code || "—"} />
              </Grid.Col>
            </Grid>
          </Section>

          <Section num="02" label="Details">
            <Grid gutter={{ base: 14, sm: 20 }}>
              <Grid.Col span={6}>
                <TextLabel label="Rooms" text={String(property.rooms_count)} />
              </Grid.Col>
              <Grid.Col span={6}>
                <TextLabel
                  label="Internet"
                  text={yesNoUnknown(property.has_internet)}
                />
              </Grid.Col>
            </Grid>
          </Section>

          <Section num="03" label="SES">
            <Grid gutter={{ base: 14, sm: 20 }}>
              <Grid.Col span={12}>
                <TextLabel
                  label="Establishment code"
                  text={property.ses_establishment_code || "Not set"}
                  mono={property.ses_establishment_code != null}
                />
              </Grid.Col>
            </Grid>
          </Section>

          <Section num="04" label="Channels">
            {calendars.length === 0 ? (
              <p className="text-sm text-slate-400">No channels configured</p>
            ) : (
              <div className="flex flex-col gap-4">
                {calendars.map((cal) => (
                  <div key={cal.channel} className="flex flex-col gap-1">
                    <div className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                      {cal.channel}
                    </div>
                    <a
                      href={cal.url}
                      target="_blank"
                      rel="noreferrer"
                      className="text-xs font-mono text-sky-600 break-all hover:underline"
                    >
                      {cal.url}
                    </a>
                    <div className="text-xs text-slate-400">
                      {cal.lastSyncAt
                        ? `Last synced ${formatLongDate(cal.lastSyncAt)}`
                        : "Never synced"}
                    </div>
                    {cal.lastError && (
                      <div className="text-xs text-red-500 break-words">
                        {cal.lastError}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </Section>

          <Section num="05" label="Owner">
            <Grid gutter={{ base: 14, sm: 20 }}>
              <Grid.Col span={6}>
                <TextLabel label="Name" text={ownerFullName || "—"} />
              </Grid.Col>
              <Grid.Col span={6}>
                <TextLabel label="Email" text={property.owner.email || "—"} />
              </Grid.Col>
            </Grid>
          </Section>

          <Section num="06" label="Created">
            <Grid gutter={{ base: 14, sm: 20 }}>
              <Grid.Col span={12}>
                <TextLabel
                  label="Created at"
                  text={formatLongDate(property.created_at)}
                />
              </Grid.Col>
            </Grid>
          </Section>
        </>
      )}
    </DetailsDrawer>
  );
}
