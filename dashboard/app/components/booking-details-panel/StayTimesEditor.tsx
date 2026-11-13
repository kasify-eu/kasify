import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { TimePicker } from "@mantine/dates";
import { MainButton } from "~/components/main-button/main-button";
import shared from "~/components/details-drawer/details-drawer.module.css";
import local from "./booking-details-panel.module.css";

const classes = { ...shared, ...local };

const ARRIVAL_PRESETS = ["14:00", "15:00", "16:00", "17:00"];
const DEPARTURE_PRESETS = ["09:00", "10:00", "11:00", "12:00"];

interface StayTimesEditorProps {
  checkinTime: string | null;
  checkoutTime: string | null;
  checkInShort: string;
  checkOutShort: string;
  totalNights: number;
  loading?: boolean;
  onSave: (checkinTime: string | null, checkoutTime: string | null) => void;
  onCancel: () => void;
}

function toPickerValue(value: string | null): string {
  if (!value) return "";
  const [h = "", m = ""] = value.split(":");
  return `${h.padStart(2, "0")}:${m.padStart(2, "0")}`;
}

function fromPickerValue(value: string): string | null {
  if (!value) return null;
  const [h, m] = value.split(":");
  if (!h || !m) return null;
  return `${h.padStart(2, "0")}:${m.padStart(2, "0")}`;
}

interface PresetRowProps {
  presets: readonly string[];
  onPick: (preset: string) => void;
}

function PresetRow({ presets, onPick }: PresetRowProps) {
  return (
    <div className={classes.timeInlinePresets}>
      {presets.map((preset) => (
        <button type="button" key={preset} onClick={() => onPick(preset)}>
          {preset}
        </button>
      ))}
    </div>
  );
}

export function StayTimesEditor({
  checkinTime,
  checkoutTime,
  checkInShort,
  checkOutShort,
  totalNights,
  loading = false,
  onSave,
  onCancel,
}: StayTimesEditorProps) {
  const { t } = useTranslation();
  const [arrival, setArrival] = useState("");
  const [departure, setDeparture] = useState("");

  useEffect(() => {
    setArrival(toPickerValue(checkinTime));
    setDeparture(toPickerValue(checkoutTime));
  }, [checkinTime, checkoutTime]);

  const handleSave = () => {
    onSave(fromPickerValue(arrival), fromPickerValue(departure));
  };

  return (
    <>
      <div className={classes.stayBar}>
        <div className={classes.stayCol}>
          <div className={classes.stayLabel}>
            {t("bookingDetails.checkIn") as string}
          </div>
          <div className={classes.stayVal}>{checkInShort}</div>
          <TimePicker
            value={arrival}
            onChange={setArrival}
            size="xs"
            className={classes.timeInlinePicker}
            hoursInputLabel={t("bookingDetails.stayTimes.checkInHour") as string}
            minutesInputLabel={
              t("bookingDetails.stayTimes.checkInMinute") as string
            }
          />
          <PresetRow presets={ARRIVAL_PRESETS} onPick={setArrival} />
        </div>
        <div className={classes.stayMiddle}>
          <div className="count">
            {totalNights} {t("bookingDetails.nights") as string}
          </div>
        </div>
        <div className={`${classes.stayCol} ${classes.stayColEnd}`}>
          <div className={classes.stayLabel}>
            {t("bookingDetails.checkOut") as string}
          </div>
          <div className={classes.stayVal}>{checkOutShort}</div>
          <TimePicker
            value={departure}
            onChange={setDeparture}
            size="xs"
            className={classes.timeInlinePicker}
            hoursInputLabel={
              t("bookingDetails.stayTimes.checkOutHour") as string
            }
            minutesInputLabel={
              t("bookingDetails.stayTimes.checkOutMinute") as string
            }
          />
          <PresetRow presets={DEPARTURE_PRESETS} onPick={setDeparture} />
        </div>
      </div>

      <div className={classes.stayTimesFoot}>
        <MainButton variant="ghost" onClick={onCancel}>
          {t("bookingDetails.cancel") as string}
        </MainButton>
        <MainButton variant="primary" onClick={handleSave} loading={loading}>
          {t("bookingDetails.save") as string}
        </MainButton>
      </div>
    </>
  );
}
