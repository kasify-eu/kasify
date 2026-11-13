import { useState } from "react";
import { useLoaderData, useNavigate, useSearchParams } from "react-router";
import { useTranslation } from "react-i18next";
import {
  ActionIcon,
  Box,
  Container,
  Drawer,
  Group,
  Pagination,
} from "@mantine/core";
import { IconFilter, IconX } from "@tabler/icons-react";
import { ApiClient, createApiConfig, handleApiError } from "~/lib/api-client";
import { ContentHeader } from "~/components/content-header";
import { AdminSearchFilterPanel } from "~/components/admin/admin-search-filter-panel";
import type { User } from "~/types/user";
import type { Route } from "./+types/users";

const PAGE_SIZE = 20;

export function meta({}: Route.MetaArgs) {
  return [{ title: "Kasify Admin | Users" }];
}

export async function loader({ request }: Route.LoaderArgs) {
  const url = new URL(request.url);
  const pageRaw = Number.parseInt(url.searchParams.get("page") ?? "0", 10);
  const page = Number.isFinite(pageRaw) && pageRaw >= 0 ? pageRaw : 0;
  const query = url.searchParams.get("query") || undefined;

  try {
    const apiClient = await ApiClient.createAuthenticated(
      request,
      createApiConfig(),
    );
    const response = await apiClient.getAdminUsers({
      query,
      page,
      size: PAGE_SIZE,
    });
    return { data: response.data, page, error: null as string | null };
  } catch (error) {
    const apiError = handleApiError(error);
    return {
      data: { items: [] as User[], page: 0, size: PAGE_SIZE, total: 0 },
      page,
      error: apiError.message,
    };
  }
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return "—";
  const date = new Date(dateStr);
  if (isNaN(date.getTime())) return "—";
  return date.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

export default function AdminUsers() {
  const { data, page, error } = useLoaderData<typeof loader>();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [filterDrawerOpened, setFilterDrawerOpened] = useState(false);

  if (error) {
    return (
      <Container fluid={true}>
        <ContentHeader title="Users" subtitle="Error loading users" />
        <p className="text-red-500">{error}</p>
      </Container>
    );
  }

  const items = data.items;
  const totalPages = Math.max(1, Math.ceil(data.total / PAGE_SIZE));

  const handlePageChange = (newPage: number) => {
    const params = new URLSearchParams(searchParams);
    params.set("page", String(newPage - 1));
    navigate(`/admin/users?${params.toString()}`);
  };

  return (
    <Container fluid={true}>
      <ContentHeader
        title={`Users (${data.total})`}
        subtitle="All registered users on the platform"
        right={
          <ActionIcon
            variant="default"
            size="lg"
            radius="xl"
            onClick={() => setFilterDrawerOpened(true)}
            hiddenFrom="lg"
          >
            <IconFilter size={18} />
          </ActionIcon>
        }
      />

      <div className="flex gap-6 items-start">
        <div className="flex-1 min-w-0">
          <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-slate-50 border-b border-slate-200">
                    <th className="px-2.5 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider">
                      Name
                    </th>
                    <th className="px-2.5 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider">
                      Email
                    </th>
                    <th className="px-2.5 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider">
                      Created
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {items.length === 0 && (
                    <tr>
                      <td
                        colSpan={3}
                        className="px-6 py-12 text-center text-sm text-slate-400"
                      >
                        No users match the current filter
                      </td>
                    </tr>
                  )}
                  {items.map((user) => (
                    <tr
                      key={user.id}
                      className="group hover:bg-slate-50 transition-colors"
                    >
                      <td className="px-2.5 py-4">
                        <p className="font-semibold text-xs text-slate-900">
                          {user.name} {user.last_name}
                        </p>
                      </td>
                      <td className="px-2.5 py-4">
                        <p className="text-xs text-slate-500">{user.email}</p>
                      </td>
                      <td className="px-2.5 py-4">
                        <span className="text-xs text-slate-500">
                          {formatDate(user.created_at)}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          <Group justify="center" mt="xl">
            <Pagination
              total={totalPages}
              value={page + 1}
              onChange={handlePageChange}
            />
          </Group>
        </div>
        <Box
          component="aside"
          visibleFrom="lg"
          w={240}
          style={{ flexShrink: 0, position: "sticky", top: 24 }}
        >
          <AdminSearchFilterPanel placeholder="Search name or email…" />
        </Box>
      </div>

      <Drawer
        opened={filterDrawerOpened}
        onClose={() => setFilterDrawerOpened(false)}
        position="right"
        size="xs"
        title={
          <span className="text-lg pl-2 font-semibold text-slate-800">
            {t("filters.title")}
          </span>
        }
        closeButtonProps={{
          icon: <IconX size={24} color="white" />,
          style: {
            marginTop: 6,
            marginBottom: 6,
            marginRight: 12,
            backgroundColor: "var(--mantine-color-dark-7)",
          },
          size: "xl",
        }}
        styles={{ header: { backgroundColor: "#F8FAFC" } }}
      >
        <Box pt="xl">
          <AdminSearchFilterPanel placeholder="Search name or email…" inDrawer />
        </Box>
      </Drawer>
    </Container>
  );
}
