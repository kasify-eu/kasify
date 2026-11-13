import { Select } from "@mantine/core";
import type { PropertyMember } from "~/types/properties";

interface CleanerSelectProps {
  team: PropertyMember[];
  value: string | null;
  onChange: (value: string | null) => void;
  label?: string;
  description?: string;
  disabled?: boolean;
  assignedToName?: string;
}

export function CleanerSelect({
  team,
  value,
  onChange,
  label,
  description,
  disabled,
  assignedToName,
}: CleanerSelectProps) {
  const hasTeam = team.length > 0;

  const data = hasTeam
    ? team.map((member) => ({
        value: member.id,
        label:
          member.access_level === "manager"
            ? `${member.name}${member.last_name ? ` ${member.last_name}` : ""} (Owner)`
            : `${member.name}${member.last_name ? ` ${member.last_name}` : ""}`,
      }))
    : value && assignedToName
      ? [{ value, label: assignedToName }]
      : [];

  return (
    <Select
      label={label}
      description={description}
      placeholder="Select a cleaner"
      allowDeselect={false}
      data={data}
      value={value}
      onChange={onChange}
      disabled={disabled || !hasTeam}
    />
  );
}
