// lib/api-client.ts
import type {
  CreatePropertyRequest,
  PropertiesResponse,
  Property,
  PropertyPatch,
  SmartLock,
  SmartLocksStatusResponse,
  UpdatePropertyRequest,
} from "~/types/properties";
import type {
  AssignSmartlockRequest,
  AssignUnifiSmartlockRequest,
  CreateSesHospedajesIntegrationRequest,
  IntegrationsResponse,
  NukiIntegration,
  NukiSmartlocksResponse,
  SesHospedajesIntegration,
  UnifiSmartlocksResponse,
  UnifiIntegration,
  UnifiIntegrationRequest,
  UpdateSesHospedajesSettingsRequest,
} from "~/types/integrations";
import type {Booking, BookingsResponse, CreateBookingRequest, Guest} from "~/types/bookings";
import type {CheckinResponse, GuestRegistrationRequest, MarketingUnsubscribeResponse} from "~/types/checkin";
import type {GuestsResponse} from "~/types/guests";
import type {CalendarEventsResponse} from "~/types/calendar";
import type {ActivitiesResponse, FlatActivitiesResponse} from "~/types/activities";
import type {Account, Locale} from "~/types/account";
import type {
  AcceptInvitationResponse,
  CreatePropertyInvitationRequest,
  PropertyInvitation,
  PropertyInvitationsResponse,
  CrewMembersResponse,
} from "~/types/crew";
import type {
  CreateTaskRequest,
  Task,
  TaskChecklistItem,
  TasksFilters,
  TasksResponse,
  UpdateTaskRequest,
} from "~/types/tasks";
import type { FileRecord } from "~/types/files";
import type {
  ApiToken,
  CreateApiTokenRequest,
  CreateApiTokenResponse,
} from "~/types/api-tokens";
import type {
  TelegramLinkResponse,
  TelegramStatusResponse,
} from "~/types/telegram";
import type { AdminSesBookingDetailResponse } from "~/types/ses-monitoring";
import type { AdminOverviewResponse } from "~/types/admin-overview";
import type {
  AdminBookingsFilters,
  AdminBookingsPageResponse,
  AdminGuestsFilters,
  AdminGuestsPageResponse,
  AdminPropertiesFilters,
  AdminPropertiesPageResponse,
  AdminUsersFilters,
  AdminUsersPageResponse,
} from "~/types/admin-lists";

// Types for better type safety
export interface ApiClientConfig {
  baseUrl: string;
  timeout?: number;
  defaultHeaders?: Record<string, string>;
}

export interface ApiResponse<T = any> {
  data: T;
  status: number;
  statusText: string;
  headers: Headers;
}

export class ApiClientError extends Error {
  public code?: string;

  constructor(
    message: string,
    public status: number,
    public data?: any,
  ) {
    super(message);
    this.name = "ApiClientError";
    this.code = data?.code;
  }
}

export class ApiClient {
  private config: Required<ApiClientConfig>;

  constructor(config: ApiClientConfig) {
    this.config = {
      timeout: 10000,
      defaultHeaders: {},
      ...config,
    };
  }

  /**
   * Create an authenticated API client instance for use in Remix actions/loaders
   */
  static async createAuthenticated(
    request: Request,
    config: ApiClientConfig,
  ): Promise<ApiClient> {
    // Dynamic import to avoid loading server-only code in the browser.
    // Deduped so this doesn't redeem the same single-use refresh token that
    // authMiddleware already rotated for this request.
    const { getDedupedKindeSession } = await import(
      "~/lib/kinde-session"
    );
    const { getToken } = await getDedupedKindeSession(request);

    // getToken automatically handles token refresh if needed
    // It will silently refresh the token in the background if it's about to expire
    const token = await getToken();

    if (!token) {
      throw new ApiClientError("No authentication token available", 401);
    }

    const client = new ApiClient({
      ...config,
      defaultHeaders: {
        ...config.defaultHeaders,
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
        ...buildForwardedHeaders(request),
      },
    });

    return client;
  }

  /**
   * Create an unauthenticated API client for use in server-side loaders that
   * call public endpoints (guest check-in, public task links). Forwards the
   * original client identity so the API can rate-limit by real IP.
   */
  static createPublic(request: Request, config: ApiClientConfig): ApiClient {
    return new ApiClient({
      ...config,
      defaultHeaders: {
        ...config.defaultHeaders,
        "Content-Type": "application/json",
        ...buildForwardedHeaders(request),
      },
    });
  }

  /**
   * Create an API client instance for use in browser/client-side code
   * Uses cookie-based authentication with credentials: 'include'
   */
  static createFromBrowser(config: ApiClientConfig): ApiClient {
    const client = new ApiClient({
      ...config,
      defaultHeaders: {
        ...config.defaultHeaders,
        "Content-Type": "application/json",
      },
    });

    // Mark this client as browser-based to include credentials
    (client as any)._isBrowserClient = true;

    return client;
  }

  async getProperties(): Promise<ApiResponse<PropertiesResponse>> {
    return this.get<PropertiesResponse>("/properties");
  }

  async getProperty(propertyId: string): Promise<ApiResponse<Property>> {
    return this.get<Property>(`/properties/${propertyId}`);
  }

  async getBookings(filters: {
    from?: string;
    to?: string;
    propertyId?: string;
    query?: string;
    channel?: string;
    page?: number;
    size?: number;
  } = {}): Promise<ApiResponse<BookingsResponse>> {
    const { from, to, propertyId, query, channel, page = 0, size = 15 } = filters;
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (from) params.from = from;
    if (to) params.to = to;
    if (propertyId) params.property_id = propertyId;
    if (query) params.query = query;
    if (channel) params.channel = channel;
    return this.get<BookingsResponse>("/bookings", params);
  }

  async getGuests(filters: {
    propertyId?: string;
    query?: string;
    page?: number;
    size?: number;
  } = {}): Promise<ApiResponse<GuestsResponse>> {
    const { propertyId, query, page = 0, size = 15 } = filters;
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (propertyId) params.property_id = propertyId;
    if (query) params.query = query;
    return this.get<GuestsResponse>("/guests", params);
  }

  async getCheckins(
    fromDate: string,
    page: number = 0,
    size: number = 3,
  ): Promise<ApiResponse<BookingsResponse>> {
    return this.get<BookingsResponse>(
      `/bookings?checkin_from=${fromDate}&size=${size}&page=${page}&sort=from`,
    );
  }

  async getCheckouts(
    fromDate: string,
    page: number = 0,
    size: number = 3,
  ): Promise<ApiResponse<BookingsResponse>> {
    return this.get<BookingsResponse>(
      `/bookings?checkout_from=${fromDate}&size=${size}&page=${page}&sort=to`,
    );
  }

  async getBooking(bookingId: string): Promise<ApiResponse<Booking>> {
    return this.get<Booking>(`/bookings/${bookingId}`);
  }

  async createBooking(propertyId: string, data: Omit<CreateBookingRequest, "property_id">): Promise<ApiResponse<Booking>> {
    return this.post<Booking>(`/properties/${propertyId}/bookings`, data);
  }

  async getCalendarEvents(params: {
    from?: string;
    to?: string;
  } = {}): Promise<ApiResponse<CalendarEventsResponse>> {
    const query: Record<string, string> = {};
    if (params.from) query.from = params.from;
    if (params.to) query.to = params.to;
    return this.get<CalendarEventsResponse>("/calendar", query);
  }

  async getTimeline(): Promise<ApiResponse<ActivitiesResponse>> {
    return this.get<ActivitiesResponse>("/timeline");
  }

  async getActivities(
    page: number = 0,
    size: number = 20,
    propertyId?: string,
  ): Promise<ApiResponse<FlatActivitiesResponse>> {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (propertyId) params.property_id = propertyId;
    return this.get<FlatActivitiesResponse>("/activities", params);
  }

  async createProperty(
    propertyData: CreatePropertyRequest,
  ): Promise<ApiResponse<Property>> {
    return this.post<Property>("/properties", propertyData);
  }

  async updateProperty(
    propertyId: string,
    propertyData: UpdatePropertyRequest,
  ): Promise<ApiResponse<Property>> {
    return this.put<Property>(`/properties/${propertyId}`, propertyData);
  }

  async patchProperty(
    propertyId: string,
    patch: PropertyPatch,
  ): Promise<ApiResponse<Property>> {
    return this.patch<Property>(`/properties/${propertyId}`, patch);
  }

  async deleteProperty(propertyId: string): Promise<ApiResponse<void>> {
    return this.delete<void>(`/properties/${propertyId}`);
  }

  async leaveProperty(propertyId: string): Promise<ApiResponse<void>> {
    return this.post<void>(`/properties/${propertyId}/leave`);
  }

  async getPropertySmartLocks(
    propertyId: string,
  ): Promise<ApiResponse<SmartLocksStatusResponse>> {
    return this.get<SmartLocksStatusResponse>(
      `/properties/${propertyId}/smartlocks`,
    );
  }

  async getIntegrations(): Promise<ApiResponse<IntegrationsResponse>> {
    return this.get<IntegrationsResponse>("/integrations");
  }

  async createNukiIntegration(nukiToken: string): Promise<ApiResponse<NukiIntegration>> {
    return this.post<NukiIntegration>("/integrations/nuki", {nuki_token: nukiToken});
  }

  async getNukiSmartlocks(integrationId: string): Promise<ApiResponse<NukiSmartlocksResponse>> {
    return this.get<NukiSmartlocksResponse>(`/integrations/nuki/${integrationId}/smartlocks`);
  }

  async assignSmartlockToProperty(
    propertyId: string,
    data: AssignSmartlockRequest,
  ): Promise<ApiResponse<SmartLock>> {
    return this.post<SmartLock>(
      `/properties/${propertyId}/smartlocks`,
      data,
    );
  }

  async unassignSmartlockFromProperty(
    propertyId: string,
    smartlockId: string,
  ): Promise<ApiResponse<void>> {
    return this.delete<void>(
      `/properties/${propertyId}/smartlocks/${smartlockId}`,
    );
  }

  async updateNukiIntegration(integrationId: string, nukiToken: string): Promise<ApiResponse<NukiIntegration>> {
    return this.put<NukiIntegration>(`/integrations/nuki/${integrationId}`, {nuki_token: nukiToken});
  }

  async deleteNukiIntegration(integrationId: string): Promise<ApiResponse<void>> {
    return this.delete<void>(`/integrations/nuki/${integrationId}`);
  }

    async getUnifiSmartlocks(integrationId: string): Promise<ApiResponse<UnifiSmartlocksResponse>> {
        return this.get<UnifiSmartlocksResponse>(`/integrations/unifi/${integrationId}/smartlocks`);
    }

    async assignUnifiSmartlockToProperty(
        propertyId: string,
        data: AssignUnifiSmartlockRequest,
    ): Promise<ApiResponse<SmartLock>> {
        return this.post<SmartLock>(
            `/properties/${propertyId}/smartlocks`,
            data,
        );
    }

    async getUnifiIntegrations(): Promise<ApiResponse<UnifiIntegration[]>> {
        return this.get<UnifiIntegration[]>("/integrations/unifi");
    }

    async updateUnifiIntegration(
        integrationId: string,
        data: UnifiIntegrationRequest,
    ): Promise<ApiResponse<UnifiIntegration>> {
        return this.put<UnifiIntegration>(`/integrations/unifi/${integrationId}`, data);
    }

    async deleteUnifiIntegration(integrationId: string): Promise<ApiResponse<void>> {
        return this.delete<void>(`/integrations/unifi/${integrationId}`);
    }

    // ============================================
    // SES Hospedajes Integration Methods
    // ============================================

    // SES verifies credentials against the upstream registry on create/update,
    // which can take well over the default timeout when their service is slow.
    private static readonly SES_VERIFY_TIMEOUT_MS = 150_000;

    async getSesHospedajesIntegrations(): Promise<ApiResponse<SesHospedajesIntegration[]>> {
        return this.get<SesHospedajesIntegration[]>("/integrations/ses-hospedajes");
    }

    async createSesHospedajesIntegration(
        data: CreateSesHospedajesIntegrationRequest,
    ): Promise<ApiResponse<SesHospedajesIntegration>> {
        return this.post<SesHospedajesIntegration>(
            "/integrations/ses-hospedajes",
            data,
            {timeoutMs: ApiClient.SES_VERIFY_TIMEOUT_MS},
        );
    }

    async updateSesHospedajesIntegration(
        integrationId: string,
        data: CreateSesHospedajesIntegrationRequest,
    ): Promise<ApiResponse<SesHospedajesIntegration>> {
        return this.put<SesHospedajesIntegration>(
            `/integrations/ses-hospedajes/${integrationId}`,
            data,
            {timeoutMs: ApiClient.SES_VERIFY_TIMEOUT_MS},
        );
    }

    async updateSesHospedajesSettings(
        integrationId: string,
        data: UpdateSesHospedajesSettingsRequest,
    ): Promise<ApiResponse<SesHospedajesIntegration>> {
        return this.patch<SesHospedajesIntegration>(
            `/integrations/ses-hospedajes/${integrationId}/settings`,
            data,
        );
    }

    async deleteSesHospedajesIntegration(integrationId: string): Promise<ApiResponse<void>> {
        return this.delete<void>(`/integrations/ses-hospedajes/${integrationId}`);
    }

  async signup(data: {
    name: string;
    last_name: string;
  }): Promise<ApiResponse<any>> {
    return this.post<any>("/signup", data);
  }

  async getAccount(): Promise<ApiResponse<Account>> {
    return this.get<Account>("/account");
  }

  async updateAccount(data: {
    name: string;
    last_name: string;
    locale?: Locale;
  }): Promise<ApiResponse<Account>> {
    return this.put<Account>("/account", data);
  }

    // ============================================
    // API Tokens (Personal Access Tokens)
    // ============================================

    async getApiTokens(): Promise<ApiResponse<ApiToken[]>> {
        return this.get<ApiToken[]>("/account/tokens");
    }

    async createApiToken(
        data: CreateApiTokenRequest,
    ): Promise<ApiResponse<CreateApiTokenResponse>> {
        return this.post<CreateApiTokenResponse>("/account/tokens", data);
    }

    async revokeApiToken(tokenId: string): Promise<ApiResponse<void>> {
        return this.delete<void>(`/account/tokens/${tokenId}`);
    }

    // ============================================
    // Telegram notifications linking
    // ============================================

    async getTelegramStatus(): Promise<ApiResponse<TelegramStatusResponse>> {
        return this.get<TelegramStatusResponse>("/account/telegram");
    }

    async startTelegramLink(): Promise<ApiResponse<TelegramLinkResponse>> {
        return this.post<TelegramLinkResponse>("/account/telegram/link", {});
    }

    async unlinkTelegram(): Promise<ApiResponse<void>> {
        return this.delete<void>("/account/telegram");
    }

    // ============================================
    // Crew Management Methods
    // ============================================

    async createCrewInvitation(
        propertyId: string,
        data: CreatePropertyInvitationRequest,
    ): Promise<ApiResponse<PropertyInvitation>> {
        return this.post<PropertyInvitation>(
            `/properties/${propertyId}/invitations`,
            data,
        );
    }

    async getCrewInvitations(
        propertyId: string,
    ): Promise<ApiResponse<PropertyInvitationsResponse>> {
        return this.get<PropertyInvitationsResponse>(
            `/properties/${propertyId}/invitations`,
        );
    }

    async acceptInvitation(
        token: string,
    ): Promise<ApiResponse<AcceptInvitationResponse>> {
        return this.post<AcceptInvitationResponse>(`/invitations/${token}/accept`);
    }

    async getPropertyCrew(
        propertyId: string,
    ): Promise<ApiResponse<CrewMembersResponse>> {
        return this.get<CrewMembersResponse>(`/properties/${propertyId}/crew`);
    }

    async removeCrewMember(
        propertyId: string,
        userId: string,
    ): Promise<ApiResponse<void>> {
        return this.delete<void>(`/properties/${propertyId}/crew/${userId}`);
    }

    // ============================================
    // Task Management Methods
    // ============================================

    async createTask(data: CreateTaskRequest): Promise<ApiResponse<Task>> {
        return this.post<Task>("/tasks", data);
    }

    async getTasks(filters?: TasksFilters): Promise<ApiResponse<TasksResponse>> {
        const params: Record<string, string> = {};
        if (filters?.property_id) params.property_id = filters.property_id;
        if (filters?.assigned_to) params.assigned_to = filters.assigned_to;
        if (filters?.status) params.status = filters.status;
        if (filters?.task_type) params.task_type = filters.task_type;
        if (filters?.scheduled_from) params.scheduled_from = filters.scheduled_from;
        if (filters?.scheduled_to) params.scheduled_to = filters.scheduled_to;
        if (filters?.include_cancelled)
            params.include_cancelled = String(filters.include_cancelled);
        if (filters?.page != null) params.page = String(filters.page);
        if (filters?.size != null) params.size = String(filters.size);

        return this.get<TasksResponse>("/tasks", params);
    }

    async getTask(taskId: string): Promise<ApiResponse<Task>> {
        return this.get<Task>(`/tasks/${taskId}`);
    }

    async updateTask(
        taskId: string,
        data: UpdateTaskRequest,
    ): Promise<ApiResponse<Task>> {
        return this.patch<Task>(`/tasks/${taskId}`, data);
    }

    async deleteTask(taskId: string): Promise<ApiResponse<void>> {
        return this.delete<void>(`/tasks/${taskId}`);
    }

    async startTask(taskId: string): Promise<ApiResponse<Task>> {
        return this.post<Task>(`/tasks/${taskId}/start`);
    }

    async completeTask(taskId: string): Promise<ApiResponse<Task>> {
        return this.post<Task>(`/tasks/${taskId}/complete`);
    }

    async toggleChecklistItem(
        taskId: string,
        itemId: string,
        completed: boolean,
    ): Promise<ApiResponse<TaskChecklistItem>> {
        return this.patch<TaskChecklistItem>(`/tasks/${taskId}/checklist/${itemId}`, {
            completed,
        });
    }

    async getPublicTask(token: string): Promise<ApiResponse<Task>> {
        return this.get<Task>(`/public/tasks/${token}`);
    }

    // ============================================
    // Public Guest Check-in
    // ============================================

    async getCheckin(bookingCode: string): Promise<ApiResponse<CheckinResponse>> {
        return this.get<CheckinResponse>(`/checkin/${bookingCode}`);
    }

    async registerGuest(
        bookingCode: string,
        data: GuestRegistrationRequest,
    ): Promise<ApiResponse<CheckinResponse>> {
        return this.post<CheckinResponse>(`/checkin/${bookingCode}/guests`, data);
    }

    // Public marketing unsubscribe — the target of the opt-out link embedded in
    // every marketing email. Idempotent; authenticated by the token alone.
    async unsubscribeMarketing(token: string): Promise<ApiResponse<MarketingUnsubscribeResponse>> {
        return this.post<MarketingUnsubscribeResponse>(`/marketing/unsubscribe/${encodeURIComponent(token)}`);
    }

    // Owner-side guest management. The drawer in the booking details panel
    // submits the same request shape as the public check-in form.
    async createBookingGuest(
        bookingId: string,
        data: GuestRegistrationRequest,
    ): Promise<ApiResponse<Guest>> {
        return this.post<Guest>(`/bookings/${bookingId}/guests`, data);
    }

    async updateGuest(
        guestId: string,
        data: GuestRegistrationRequest,
    ): Promise<ApiResponse<Guest>> {
        return this.patch<Guest>(`/guests/${guestId}`, data);
    }

    async deleteGuest(guestId: string): Promise<ApiResponse<void>> {
        return this.delete<void>(`/guests/${guestId}`);
    }

    async getPropertyTasks(
        propertyId: string,
        filters?: {
            status?: string;
            include_cancelled?: boolean;
            page?: number;
            size?: number;
        },
    ): Promise<ApiResponse<TasksResponse>> {
        const params: Record<string, string> = {};
        if (filters?.status) params.status = filters.status;
        if (filters?.include_cancelled)
            params.include_cancelled = String(filters.include_cancelled);
        if (filters?.page != null) params.page = String(filters.page);
        if (filters?.size != null) params.size = String(filters.size);

        return this.get<TasksResponse>(`/properties/${propertyId}/tasks`, params);
    }

  // ============================================
    // Admin Methods
    // ============================================

    async getAdminUsers(
        filters: AdminUsersFilters = {},
    ): Promise<ApiResponse<AdminUsersPageResponse>> {
        const params: Record<string, string> = {};
        if (filters.page !== undefined) params.page = String(filters.page);
        if (filters.size !== undefined) params.size = String(filters.size);
        if (filters.query) params.query = filters.query;
        return this.get<AdminUsersPageResponse>("/admin/users", params);
    }

    async getAdminOverview(): Promise<ApiResponse<AdminOverviewResponse>> {
        return this.get<AdminOverviewResponse>("/admin/overview");
    }

    async getAdminProperties(
        filters: AdminPropertiesFilters = {},
    ): Promise<ApiResponse<AdminPropertiesPageResponse>> {
        const params: Record<string, string> = {};
        if (filters.page !== undefined) params.page = String(filters.page);
        if (filters.size !== undefined) params.size = String(filters.size);
        if (filters.query) params.query = filters.query;
        if (filters.property_id) params.property_id = filters.property_id;
        if (filters.ses) params.ses = filters.ses;
        if (filters.nuki) params.nuki = filters.nuki;
        if (filters.unifi) params.unifi = filters.unifi;
        return this.get<AdminPropertiesPageResponse>("/admin/properties", params);
    }

    async getAdminBookings(
        filters: AdminBookingsFilters = {},
    ): Promise<ApiResponse<AdminBookingsPageResponse>> {
        const params: Record<string, string> = {};
        if (filters.page !== undefined) params.page = String(filters.page);
        if (filters.size !== undefined) params.size = String(filters.size);
        if (filters.from) params.from = filters.from;
        if (filters.to) params.to = filters.to;
        if (filters.property_id) params.property_id = filters.property_id;
        if (filters.query) params.query = filters.query;
        if (filters.channel) params.channel = filters.channel;
        return this.get<AdminBookingsPageResponse>("/admin/bookings", params);
    }

    async getAdminBooking(bookingId: string): Promise<ApiResponse<Booking>> {
        return this.get<Booking>(`/admin/bookings/${bookingId}`);
    }

    async getAdminGuests(
        filters: AdminGuestsFilters = {},
    ): Promise<ApiResponse<AdminGuestsPageResponse>> {
        const params: Record<string, string> = {};
        if (filters.page !== undefined) params.page = String(filters.page);
        if (filters.size !== undefined) params.size = String(filters.size);
        if (filters.property_id) params.property_id = filters.property_id;
        if (filters.channel_id) params.channel_id = filters.channel_id;
        if (filters.query) params.query = filters.query;
        return this.get<AdminGuestsPageResponse>("/admin/guests", params);
    }

    async getAdminSesBookingDetail(
        bookingId: string,
    ): Promise<ApiResponse<AdminSesBookingDetailResponse>> {
        return this.get<AdminSesBookingDetailResponse>(
            `/admin/ses/bookings/${bookingId}/submissions`,
        );
    }

    async submitAdminSesBooking(
        bookingId: string,
    ): Promise<ApiResponse<AdminSesBookingDetailResponse>> {
        return this.post<AdminSesBookingDetailResponse>(
            `/admin/ses/bookings/${bookingId}/submit`,
            {},
        );
    }

    async submitAdminSesBookingRh(
        bookingId: string,
    ): Promise<ApiResponse<AdminSesBookingDetailResponse>> {
        return this.post<AdminSesBookingDetailResponse>(
            `/admin/ses/bookings/${bookingId}/submit-rh`,
            {},
        );
    }

    async cancelAdminSesCommunication(
        communicationId: string,
    ): Promise<ApiResponse<AdminSesBookingDetailResponse>> {
        return this.post<AdminSesBookingDetailResponse>(
            `/admin/ses/communications/${communicationId}/cancel`,
            {},
        );
    }

    async pollAdminSesCommunication(
        communicationId: string,
    ): Promise<ApiResponse<AdminSesBookingDetailResponse>> {
        return this.post<AdminSesBookingDetailResponse>(
            `/admin/ses/communications/${communicationId}/poll`,
            {},
        );
    }

  async getPropertyBookings(
      propertyId: string,
      filters?: {
        state?: string;
        size?: number;
        page?: number;
      },
  ): Promise<ApiResponse<BookingsResponse>> {
    const params: Record<string, string> = {};
    if (filters?.state) params.state = filters.state;
    if (filters?.size) params.size = String(filters.size);
    if (filters?.page) params.page = String(filters.page);

    return this.get<BookingsResponse>(`/properties/${propertyId}/bookings`, params);
  }

  private async makeRequest<T>(
    endpoint: string,
    options: RequestInit = {},
    timeoutMs?: number,
  ): Promise<ApiResponse<T>> {
    const url = `${this.config.baseUrl}${endpoint}`;

    // Merge headers — omit Content-Type for FormData so the browser sets the
    // correct multipart boundary automatically
    const isFormData =
      typeof FormData !== "undefined" && options.body instanceof FormData;
    const merged: Record<string, string> = {
      ...this.config.defaultHeaders,
      ...Object.fromEntries(new Headers(options.headers || {}).entries()),
    };
    if (isFormData) {
      delete merged["Content-Type"];
      delete merged["content-type"];
    }
    const headers = new Headers(merged);

    // Create AbortController for timeout
    const controller = new AbortController();
    const effectiveTimeout = timeoutMs ?? this.config.timeout;
    const timeoutId = setTimeout(() => controller.abort(), effectiveTimeout);

    // Add credentials: 'include' for browser-based clients to send cookies
    const isBrowserClient = (this as any)._isBrowserClient;

    try {
      const response = await fetch(url, {
        ...options,
        headers,
        signal: controller.signal,
        ...(isBrowserClient && {
          credentials: "include" as RequestCredentials,
        }),
      });

      clearTimeout(timeoutId);

      // Handle 401 Unauthorized - token expired or invalid
      if (response.status === 401 && isBrowserClient) {
        this.handle401Unauthorized();
        throw new ApiClientError(
          "Authentication expired. Redirecting to login...",
          401,
        );
      }

      // Handle non-2xx responses
      if (!response.ok) {
        const errorData = await this.parseErrorResponse(response);
        throw new ApiClientError(
          errorData.message || `HTTP ${response.status}: ${response.statusText}`,
          response.status,
          errorData,
        );
      }

      const data = await this.parseResponse<T>(response);

      return {
        data,
        status: response.status,
        statusText: response.statusText,
        headers: response.headers,
      };
    } catch (error) {
      clearTimeout(timeoutId);

      if (error instanceof ApiClientError) {
        throw error;
      }

      if (error instanceof Error && error.name === "AbortError") {
        throw new ApiClientError("Request timeout", 408);
      }

      throw new ApiClientError(
        error instanceof Error ? error.message : "Network error",
        0,
      );
    }
  }

  /**
   * Handle 401 Unauthorized errors by redirecting to session expired handler
   * Passes current location as query param to return after re-authentication
   *
   * Note: Uses /session-expired (not /refresh) to avoid breaking signup workflow
   */
  private handle401Unauthorized(): void {
    if (typeof window === "undefined") return;

    // Show toast notification if available
    try {
      // Dynamic import to avoid errors if toast is not available
      import("~/lib/toast").then(({ showInfoToast }) => {
        showInfoToast("Session expired. Refreshing authentication...", "🔄");
      });
    } catch {
      // Toast not available, continue with redirect
    }

    // Get current location to redirect back after re-authentication
    const currentPath = window.location.pathname + window.location.search;

    // Redirect to session-expired route with redirect URL
    // Note: We use /session-expired instead of /refresh because /refresh
    // is reserved for the signup workflow which always forces re-authentication
    const expiredUrl = `/session-expired?redirectTo=${encodeURIComponent(currentPath)}`;

    // Small delay to allow toast to show
    setTimeout(() => {
      window.location.href = expiredUrl;
    }, 500);
  }

  private async parseResponse<T>(response: Response): Promise<T> {
    const contentType = response.headers.get("content-type") || "";

    if (contentType.includes("application/json")) {
      return response.json();
    }

    if (contentType.includes("text/")) {
      return response.text() as T;
    }

    // For other types, return as blob or handle as needed
    return response.blob() as T;
  }

  private async parseErrorResponse(response: Response) {
    try {
      const contentType = response.headers.get("content-type") || "";
      if (contentType.includes("application/json")) {
        return await response.json();
      }
      return { message: await response.text() };
    } catch {
      return { message: response.statusText };
    }
  }

  // HTTP Methods
  async get<T>(
    endpoint: string,
    params?: Record<string, string>,
  ): Promise<ApiResponse<T>> {
    const url = params
      ? `${endpoint}?${new URLSearchParams(params).toString()}`
      : endpoint;

    return this.makeRequest<T>(url, { method: "GET" });
  }

  async post<T>(
    endpoint: string,
    body?: any,
    options?: Omit<RequestInit, "method" | "body"> & { timeoutMs?: number },
  ): Promise<ApiResponse<T>> {
    const { timeoutMs, ...rest } = options ?? {};
    return this.makeRequest<T>(
      endpoint,
      {
        method: "POST",
        body: body ? JSON.stringify(body) : undefined,
        ...rest,
      },
      timeoutMs,
    );
  }

  async put<T>(
    endpoint: string,
    body?: any,
    options?: Omit<RequestInit, "method" | "body"> & { timeoutMs?: number },
  ): Promise<ApiResponse<T>> {
    const { timeoutMs, ...rest } = options ?? {};
    return this.makeRequest<T>(
      endpoint,
      {
        method: "PUT",
        body: body ? JSON.stringify(body) : undefined,
        ...rest,
      },
      timeoutMs,
    );
  }

  async patch<T>(
    endpoint: string,
    body?: any,
    options?: Omit<RequestInit, "method" | "body">,
  ): Promise<ApiResponse<T>> {
    return this.makeRequest<T>(endpoint, {
      method: "PATCH",
      body: body ? JSON.stringify(body) : undefined,
      ...options,
    });
  }

  async delete<T>(endpoint: string): Promise<ApiResponse<T>> {
    return this.makeRequest<T>(endpoint, { method: "DELETE" });
  }

  // Property cover image
  async uploadPropertyCoverImage(
    propertyId: string,
    file: File,
  ): Promise<ApiResponse<FileRecord>> {
    return this.uploadFile<FileRecord>(
      `/properties/${propertyId}/cover-image`,
      file,
    );
  }

  async deletePropertyCoverImage(
    propertyId: string,
  ): Promise<ApiResponse<void>> {
    return this.delete<void>(`/properties/${propertyId}/cover-image`);
  }

  // File upload helper
  async uploadFile<T>(
    endpoint: string,
    file: File,
    fieldName: string = "file",
    additionalFields?: Record<string, string>,
  ): Promise<ApiResponse<T>> {
    const formData = new FormData();
    formData.append(fieldName, file);

    if (additionalFields) {
      Object.entries(additionalFields).forEach(([key, value]) => {
        formData.append(key, value);
      });
    }

    return this.makeRequest<T>(endpoint, {
      method: "POST",
      body: formData,
    });
  }
}

// Propagate the original client's identity to the API on server-side calls.
// SSR loaders run inside the dashboard and call the API directly (bypassing
// /api/$.tsx), so we need to set X-Forwarded-* here too. With Traefik's
// trustedIPs configured to include the Docker network, the API ends up
// receiving X-Forwarded-For: <real-client>, <dashboard-container>.
function buildForwardedHeaders(request: Request): Record<string, string> {
  const headers: Record<string, string> = {};
  const xff = request.headers.get("x-forwarded-for");
  if (xff) headers["x-forwarded-for"] = xff;
  try {
    const url = new URL(request.url);
    headers["x-forwarded-host"] = url.host;
    headers["x-forwarded-proto"] = url.protocol.replace(":", "");
  } catch {
    // request.url not parseable — skip host/proto, XFF alone is still useful
  }
  return headers;
}

// Utility function to create API client configuration
export function createApiConfig(): ApiClientConfig {
  // For client-side requests, use /api proxy to handle auth
  // For server-side requests, use the actual API URL
  // The /v1 suffix is the API version prefix; unversioned endpoints (e.g. /health)
  // are not consumed via this client.
  const baseUrl =
    typeof window !== "undefined"
      ? "/api/v1" // Client-side: use proxy route
      : `${process.env.VITE_KASIFY_API_URL || "http://localhost:8080"}/v1`; // Server-side: direct API

  return {
    baseUrl: baseUrl,
    timeout: 15000,
    defaultHeaders: {
      Accept: "application/json",
    },
  };
}

// Helper function for error handling in actions/loaders
export function handleApiError(error: unknown) {
  if (error instanceof ApiClientError) {
    return {
      error: true,
      code: error.code,
      message: error.message,
      status: error.status,
      data: error.data,
    };
  }

  return {
    error: true,
    code: undefined as string | undefined,
    message: error instanceof Error ? error.message : "Unknown error occurred",
    status: 500,
  };
}
