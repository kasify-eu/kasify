import { useState, useEffect, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { Grid } from "@mantine/core";
import { EditableTextarea } from "~/components/editable-textarea";
import { NumberStepper } from "~/components/number-stepper/number-stepper";
import { UnsavedChangesBar } from "~/components/unsaved-changes-bar/unsaved-changes-bar";
import classes from "~/components/details-drawer/details-drawer.module.css";
import { PAYMENT_TYPES, type PaymentType } from "~/types/bookings";
import { paymentTypeLabel } from "~/lib/payment-types";

interface GuestFormData {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  adults: number;
  children: number;
  infants: number;
  paymentType: PaymentType;
  specialRequests: string;
}

interface GuestDetailsFormProps {
  sectionNum: string;
  initialData: GuestFormData;
  onSave: (data: GuestFormData) => Promise<void>;
  loading?: boolean;
  canEdit?: boolean;
  showFirstName?: boolean;
  showLastName?: boolean;
  contactRequired?: boolean;
}

export function GuestDetailsForm({
  sectionNum,
  initialData,
  onSave,
  loading = false,
  canEdit = true,
  showFirstName = true,
  showLastName = true,
  contactRequired = false,
}: GuestDetailsFormProps) {
  const { t } = useTranslation();
  const [formData, setFormData] = useState(initialData);

  useEffect(() => {
    setFormData(initialData);
  }, [initialData]);

  const dirty = useMemo(() => {
    return (
      formData.firstName !== initialData.firstName ||
      formData.lastName !== initialData.lastName ||
      formData.email !== initialData.email ||
      formData.phone !== initialData.phone ||
      formData.adults !== initialData.adults ||
      formData.children !== initialData.children ||
      formData.infants !== initialData.infants ||
      formData.paymentType !== initialData.paymentType ||
      formData.specialRequests !== initialData.specialRequests
    );
  }, [formData, initialData]);

  // SES Hospedajes (Reserva de Hospedaje) requires the titular to have at least one contact, but RH
  // is only filed for direct bookings — so only enforce it there ([contactRequired]). OTA channels
  // (Airbnb, Booking, …) self-file with SES and must stay editable without a phone or email.
  const contactMissing =
    contactRequired &&
    formData.email.trim() === "" &&
    formData.phone.trim() === "";

  const handleSave = async () => {
    await onSave(formData);
  };

  const handleCancel = () => {
    setFormData(initialData);
  };

  return (
    <div className={classes.section}>
      <div className={classes.sectionHead}>
        <div className={classes.sectionLabel}>
          <span className={classes.num}>{sectionNum}</span>
          {t("bookingDetails.guestDetails") as string}
        </div>
        {!canEdit && (
          <span className={classes.pill}>
            <span className="dotp" />
            {t("bookingDetails.readOnly") as string}
          </span>
        )}
      </div>

      <Grid gutter={{ base: 14, sm: 20 }}>
        {showFirstName && (
          <Grid.Col span={6}>
            <div className={classes.field}>
              <label className={classes.fieldLabel}>{t("bookingDetails.guestFirstName") as string}</label>
              <input
                className={classes.fieldInput}
                value={formData.firstName}
                disabled={!canEdit}
                placeholder={canEdit ? (t("bookingDetails.enterFirstName") as string) : "—"}
                onChange={(e) => {
                  const value = e.currentTarget.value;
                  setFormData((prev) => ({ ...prev, firstName: value }));
                }}
              />
            </div>
          </Grid.Col>
        )}
        {showLastName && (
          <Grid.Col span={6}>
            <div className={classes.field}>
              <label className={classes.fieldLabel}>{t("bookingDetails.guestLastName") as string}</label>
              <input
                className={classes.fieldInput}
                value={formData.lastName}
                disabled={!canEdit}
                placeholder={canEdit ? (t("bookingDetails.enterLastName") as string) : "—"}
                onChange={(e) => {
                  const value = e.currentTarget.value;
                  setFormData((prev) => ({ ...prev, lastName: value }));
                }}
              />
            </div>
          </Grid.Col>
        )}
      </Grid>

      <Grid gutter={{ base: 14, sm: 20 }} mt={14}>
        <Grid.Col span={6}>
          <div className={classes.field}>
            <label className={classes.fieldLabel}>{t("bookingDetails.guestEmail") as string}</label>
            <input
              className={classes.fieldInput}
              type="email"
              value={formData.email}
              disabled={!canEdit}
              placeholder={canEdit ? (t("bookingDetails.enterEmail") as string) : "—"}
              onChange={(e) => {
                const value = e.currentTarget.value;
                setFormData((prev) => ({ ...prev, email: value }));
              }}
            />
          </div>
        </Grid.Col>
        <Grid.Col span={6}>
          <div className={classes.field}>
            <label className={classes.fieldLabel}>{t("bookingDetails.guestPhone") as string}</label>
            <input
              className={classes.fieldInput}
              type="tel"
              maxLength={20}
              value={formData.phone}
              disabled={!canEdit}
              placeholder={canEdit ? (t("bookingDetails.enterPhone") as string) : "—"}
              onChange={(e) => {
                const value = e.currentTarget.value;
                setFormData((prev) => ({ ...prev, phone: value }));
              }}
            />
          </div>
        </Grid.Col>
      </Grid>

      <Grid gutter={10} mt={14}>
        <Grid.Col span={4}>
          <NumberStepper
            label={t("bookingDetails.adults") as string}
            value={formData.adults}
            disabled={!canEdit}
            onChange={(v) => setFormData((prev) => ({ ...prev, adults: v }))}
          />
        </Grid.Col>
        <Grid.Col span={4}>
          <NumberStepper
            label={t("bookingDetails.children") as string}
            value={formData.children}
            disabled={!canEdit}
            onChange={(v) => setFormData((prev) => ({ ...prev, children: v }))}
          />
        </Grid.Col>
        <Grid.Col span={4}>
          <NumberStepper
            label={t("bookingDetails.infants") as string}
            value={formData.infants}
            disabled={!canEdit}
            onChange={(v) => setFormData((prev) => ({ ...prev, infants: v }))}
          />
        </Grid.Col>
      </Grid>

      <Grid gutter={{ base: 14, sm: 20 }} mt={14}>
        <Grid.Col span={6}>
          <div className={classes.field}>
            <label className={classes.fieldLabel}>{t("bookingDetails.paymentType") as string}</label>
            <select
              className={classes.fieldInput}
              value={formData.paymentType}
              disabled={!canEdit}
              onChange={(e) => {
                const value = e.currentTarget.value as PaymentType;
                setFormData((prev) => ({ ...prev, paymentType: value }));
              }}
            >
              {PAYMENT_TYPES.map((value) => (
                <option key={value} value={value}>
                  {paymentTypeLabel(t, value)}
                </option>
              ))}
            </select>
          </div>
        </Grid.Col>
      </Grid>

      <EditableTextarea
          label={t("bookingDetails.specialRequests") as string}
          value={formData.specialRequests}
          edit={canEdit}
          placeholder={t("bookingDetails.enterSpecialRequests") as string}
          onChange={(e) => {
            const value = e.currentTarget.value;
            setFormData((prev) => ({ ...prev, specialRequests: value }));
          }}
          style={{ marginTop: '14px' }}
      />

      {canEdit && dirty && (
        <UnsavedChangesBar
          onCancel={handleCancel}
          onSave={handleSave}
          loading={loading}
          disabled={contactMissing}
        />
      )}
    </div>
  );
}
