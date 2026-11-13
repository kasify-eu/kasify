import {ApiClient, createApiConfig, handleApiError} from "~/lib/api-client";
import {Form, Link, redirect, useActionData, useLoaderData, useNavigation} from "react-router";
import {
    Badge,
    Box,
    Button,
    Card,
    Group,
    NumberInput,
    Select,
    SimpleGrid,
    Stack,
    Text,
    Textarea,
    TextInput,
    ThemeIcon,
    Tooltip,
} from "@mantine/core";
import {DatePickerInput} from "@mantine/dates";
import {IconAlertCircle, IconArrowLeft, IconInfoCircle, IconPlus} from "@tabler/icons-react";
import dayjs from "dayjs";
import type {Route} from "./+types/new";
import {ContentHeader} from "~/components/content-header";
import {MainButton} from "~/components/main-button/main-button";
import {useState} from "react";
import {useTranslation} from "react-i18next";
import {
    BOOKING_CHANNELS,
    DEFAULT_BOOKING_CHANNEL,
    DEFAULT_PAYMENT_TYPE,
    type BookingChannel,
    type PaymentType,
} from "~/types/bookings";
import {paymentTypeDescription, paymentTypeOptions} from "~/lib/payment-types";

function FormError({message}: {message: string}) {
    return (
        <div className="flex items-start gap-2.5 rounded-xl border border-red-200 bg-red-50 px-3.5 py-3 text-sm text-red-700">
            <IconAlertCircle size={18} className="mt-0.5 shrink-0 text-red-500"/>
            <span className="leading-snug">{message}</span>
        </div>
    );
}

function SectionHeader({number, title, subtitle}: {number: number; title: string; subtitle?: string}) {
    return (
        <Group gap="sm" wrap="nowrap">
            <ThemeIcon radius="xl" size={24} variant="light" color="gray">
                <Text size="xs" fw={700}>{number}</Text>
            </ThemeIcon>
            <Text fw={700}>{title}</Text>
            {subtitle && <Text size="sm" c="dimmed">{subtitle}</Text>}
        </Group>
    );
}

function LabelWithTag({label, tag}: {label: string; tag: string}) {
    return (
        <span style={{display: "inline-flex", alignItems: "baseline", gap: 6}}>
            {label}
            <Text component="span" size="xs" c="dimmed">{tag}</Text>
        </span>
    );
}

export async function loader({request}: Route.LoaderArgs) {
    try {
        const apiClient = await ApiClient.createAuthenticated(
            request,
            createApiConfig(),
        );
        const propertiesResponse = await apiClient.getProperties();

        return {
            properties: propertiesResponse.data.properties,
            error: null,
        };
    } catch (error) {
        const apiError = handleApiError(error);
        return {properties: [], error: apiError.message};
    }
}

export async function action({request}: Route.ActionArgs) {
    try {
        const formData = await request.formData();

        const propertyId = formData.get("propertyId") as string;
        const firstName = formData.get("firstName") as string;
        const lastName = formData.get("lastName") as string;
        const email = ((formData.get("email") as string) ?? "").trim();
        const phone = ((formData.get("phone") as string) ?? "").trim();
        const checkIn = formData.get("checkIn") as string;
        const checkOut = formData.get("checkOut") as string;
        const adults = parseInt(formData.get("adults") as string, 10) || 1;
        const children = parseInt(formData.get("children") as string, 10) || 0;
        const infants = parseInt(formData.get("infants") as string, 10) || 0;
        const paymentType = (formData.get("paymentType") as string) || DEFAULT_PAYMENT_TYPE;
        const channel = (formData.get("channel") as string) || DEFAULT_BOOKING_CHANNEL;
        const channelId = ((formData.get("channelId") as string) ?? "").trim();
        const notes = formData.get("notes") as string | null;

        if (!propertyId || !firstName || !checkIn || !checkOut) {
            return {error: "Please fill in the property, guest name, and stay dates.", success: false};
        }

        // The backend requires an external reference for non-direct bookings.
        if (channel !== "Direct" && !channelId) {
            return {error: `Please provide the channel booking ID for ${channel} bookings.`, success: false};
        }

        // SES Hospedajes (Reserva de Hospedaje) requires the guest to have at least one contact.
        // RH is only filed for direct bookings — OTA channels report reservations themselves.
        if (channel === "Direct" && !email && !phone) {
            return {error: "Please provide a guest phone number or email (at least one is required).", success: false};
        }

        const apiClient = await ApiClient.createAuthenticated(
            request,
            createApiConfig(),
        );

        const response = await apiClient.createBooking(propertyId, {
            guest_first_name: firstName,
            guest_last_name: lastName || undefined,
            guest_email: email || undefined,
            guest_phone: phone || undefined,
            from: checkIn,
            to: checkOut,
            adults_count: adults,
            children_count: children,
            infants_count: infants,
            payment_type: paymentType as PaymentType,
            channel: channel as BookingChannel,
            channel_id: channelId || undefined,
            notes: notes || undefined,
        });

        return redirect(`/bookings?bookingId=${response.data.id}`);
    } catch (error) {
        const apiError = handleApiError(error);
        return {error: apiError.message, success: false};
    }
}

function formatDateForInput(date: Date): string {
    return date.toISOString().split("T")[0];
}

export function meta({}: Route.MetaArgs) {
    return [{title: "Kasify | Create Booking"}];
}

export default function NewBooking() {
    const {properties, error: loaderError} = useLoaderData<typeof loader>();
    const actionData = useActionData<typeof action>();
    const navigation = useNavigation();
    const isSubmitting = navigation.state === "submitting";

    const {t} = useTranslation();
    const [selectedProperty, setSelectedProperty] = useState<string | null>(null);
    const [paymentType, setPaymentType] = useState<string | null>(DEFAULT_PAYMENT_TYPE);
    const [channel, setChannel] = useState<string | null>(DEFAULT_BOOKING_CHANNEL);
    const isDirectChannel = channel === "Direct";
    const [checkIn, setCheckIn] = useState(formatDateForInput(new Date()));
    const [checkOut, setCheckOut] = useState("");
    const [adults, setAdults] = useState<string | number>(1);
    const [children, setChildren] = useState<string | number>(0);
    const [infants, setInfants] = useState<string | number>(0);
    const [firstName, setFirstName] = useState("");
    const [lastName, setLastName] = useState("");
    const [showNotes, setShowNotes] = useState(false);

    const nights = checkIn && checkOut ? dayjs(checkOut).diff(dayjs(checkIn), "day") : 0;
    const propertyName = properties.find((p) => p.id === selectedProperty)?.name;
    const guestName = [firstName.trim(), lastName.trim()].filter(Boolean).join(" ");
    const totalGuests = (Number(adults) || 0) + (Number(children) || 0) + (Number(infants) || 0);
    const channelLabel = BOOKING_CHANNELS.find((c) => c.value === channel)?.label;
    const summaryParts = [
        propertyName,
        nights > 0 ? `${dayjs(checkIn).format("D MMM")} – ${dayjs(checkOut).format("D MMM")}` : undefined,
        totalGuests > 0 ? t("newBooking.guests", {count: totalGuests}) : undefined,
        channelLabel,
    ].filter(Boolean);

    const breadcrumbItems = [
        {title: "Bookings", href: "/bookings"},
        {title: "Manual Booking", href: "#"},
    ];

    if (loaderError) {
        return (
            <Box>
                <Link to="/bookings">
                    <Button variant="light" leftSection={<IconArrowLeft size={16}/>}>
                        Back to Bookings
                    </Button>
                </Link>
                <Box mt="md">
                    <FormError message={loaderError}/>
                </Box>
            </Box>
        );
    }

    const propertyOptions = properties.map((p) => ({
        value: p.id,
        label: p.name,
    }));

    return (
        <Box>
            <ContentHeader
                breadcrumbs={breadcrumbItems}
                subtitle="Add a reservation not imported from a booking channel."
            />

            <Card withBorder p="xl" maw={720} radius="lg">
                <Form method="post">
                    <Stack gap="lg">
                        {actionData?.error && <FormError message={actionData.error}/>}

                        {/* 1 · Stay */}
                        <SectionHeader number={1} title={t("newBooking.sectionStay")}/>

                        <Select
                            label="Select Property"
                            placeholder="Search and select a property..."
                            data={propertyOptions}
                            value={selectedProperty}
                            onChange={setSelectedProperty}
                            required
                            searchable
                            name="propertyId"
                        />

                        <Group align="flex-end" wrap="nowrap">
                            <Box style={{flex: 1}}>
                                <DatePickerInput
                                    label="Check-in Date"
                                    value={checkIn || null}
                                    onChange={(value) => setCheckIn(value ?? "")}
                                    valueFormat="MMM D, YYYY"
                                    required
                                />
                                <input type="hidden" name="checkIn" value={checkIn}/>
                            </Box>
                            <Box style={{flex: 1}}>
                                <DatePickerInput
                                    label="Check-out Date"
                                    value={checkOut || null}
                                    onChange={(value) => setCheckOut(value ?? "")}
                                    valueFormat="MMM D, YYYY"
                                    required
                                    minDate={checkIn || undefined}
                                />
                                <input type="hidden" name="checkOut" value={checkOut}/>
                            </Box>
                            {nights > 0 && (
                                <Badge size="lg" h={36} radius="sm" variant="light" color="gray" tt="none" fw={600}>
                                    {t("newBooking.nights", {count: nights})}
                                </Badge>
                            )}
                        </Group>

                        <SimpleGrid cols={3}>
                            <NumberInput
                                label="Adults"
                                min={1}
                                value={adults}
                                onChange={setAdults}
                            />
                            <NumberInput
                                label={<LabelWithTag label="Kids" tag={t("newBooking.optional")}/>}
                                min={0}
                                value={children}
                                onChange={setChildren}
                            />
                            <NumberInput
                                label={<LabelWithTag label="Infants" tag={t("newBooking.optional")}/>}
                                min={0}
                                value={infants}
                                onChange={setInfants}
                            />
                        </SimpleGrid>
                        <input type="hidden" name="adults" value={String(adults)}/>
                        <input type="hidden" name="children" value={String(children)}/>
                        <input type="hidden" name="infants" value={String(infants)}/>

                        <Box mt="md"/>

                        {/* 2 · Guest */}
                        <SectionHeader number={2} title={t("newBooking.sectionGuest")}/>

                        <Group grow>
                            <TextInput
                                label="First Name"
                                placeholder="e.g. Sarah"
                                name="firstName"
                                value={firstName}
                                onChange={(e) => setFirstName(e.target.value)}
                                required
                            />
                            <TextInput
                                label="Last Name"
                                placeholder="e.g. Miller"
                                name="lastName"
                                value={lastName}
                                onChange={(e) => setLastName(e.target.value)}
                            />
                        </Group>

                        <Group grow>
                            <TextInput
                                label={
                                    isDirectChannel
                                        ? "Email"
                                        : <LabelWithTag label="Email" tag={t("newBooking.optional")}/>
                                }
                                type="email"
                                placeholder="e.g. sarah@example.com"
                                name="email"
                            />
                            <TextInput
                                label={
                                    isDirectChannel
                                        ? "Phone"
                                        : <LabelWithTag label="Phone" tag={t("newBooking.optional")}/>
                                }
                                type="tel"
                                placeholder="e.g. +34 600 000 000"
                                name="phone"
                                maxLength={20}
                            />
                        </Group>

                        <Box mt="md"/>

                        {/* 3 · Source */}
                        <SectionHeader number={3} title={t("newBooking.sectionSource")}/>

                        <Group grow align="flex-start">
                            <Select
                                label={t("newBooking.channel")}
                                data={BOOKING_CHANNELS.filter((c) =>
                                    ["Direct", "Airbnb", "Booking"].includes(c.value),
                                )}
                                value={channel}
                                onChange={setChannel}
                                name="channel"
                                allowDeselect={false}
                            />
                            <TextInput
                                label={
                                    <span style={{display: "inline-flex", alignItems: "center", gap: 4}}>
                                        {t("newBooking.channelId")}
                                        <Tooltip
                                            label={
                                                isDirectChannel
                                                    ? t("newBooking.channelIdOptional")
                                                    : t("newBooking.channelIdRequired")
                                            }
                                            multiline
                                            maw={280}
                                            withArrow
                                            events={{hover: true, focus: true, touch: true}}
                                        >
                                            <IconInfoCircle size={14} stroke={1.5} style={{cursor: "help"}}/>
                                        </Tooltip>
                                    </span>
                                }
                                placeholder={t("newBooking.channelIdPlaceholder")}
                                name="channelId"
                                maxLength={255}
                                required={!isDirectChannel}
                            />
                        </Group>

                        <Select
                            label={t("bookingDetails.paymentType")}
                            data={paymentTypeOptions(t)}
                            value={paymentType}
                            onChange={setPaymentType}
                            name="paymentType"
                            allowDeselect={false}
                            renderOption={({option}) => {
                                const description = paymentTypeDescription(t, option.value);
                                return (
                                    <Stack gap={2}>
                                        <Text size="sm">{option.label}</Text>
                                        {description && (
                                            <Text size="xs" c="dimmed">{description}</Text>
                                        )}
                                    </Stack>
                                );
                            }}
                        />

                        {showNotes ? (
                            <Textarea
                                label="Internal Notes"
                                placeholder="Notes visible only to your team"
                                name="notes"
                                minRows={4}
                                autoFocus
                            />
                        ) : (
                            <Box>
                                <Button
                                    variant="subtle"
                                    color="gray"
                                    size="compact-sm"
                                    leftSection={<IconPlus size={14}/>}
                                    onClick={() => setShowNotes(true)}
                                >
                                    {t("newBooking.addNote")}
                                </Button>
                            </Box>
                        )}

                        <Box mt="md"/>

                        {/* Footer: live summary + actions */}
                        <Group justify="space-between" align="center" wrap="nowrap">
                            <Text size="sm" c="dimmed" lineClamp={1}>
                                {guestName && (
                                    <Text component="span" size="sm" fw={600} c="var(--mantine-color-text)">
                                        {guestName}
                                    </Text>
                                )}
                                {guestName && summaryParts.length > 0 && " · "}
                                {summaryParts.join(" · ")}
                            </Text>
                            <Group gap="sm" wrap="nowrap">
                                <Link to="/bookings">
                                    <Button variant="default">Cancel</Button>
                                </Link>
                                <MainButton
                                    type="submit"
                                    loading={isSubmitting}
                                    leftSection={<IconPlus size={16}/>}
                                >
                                    Create Booking
                                </MainButton>
                            </Group>
                        </Group>
                    </Stack>
                </Form>
            </Card>
        </Box>
    );
}
