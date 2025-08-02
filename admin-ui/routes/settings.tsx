import { Handlers, PageProps } from "$fresh/server.ts";
import Layout from "../components/Layout.tsx";
import { withAuth } from "../utils/middleware.ts";
import TestConnection from "../islands/TestConnection.tsx";
import RemoveStore from "../islands/RemoveStore.tsx";
import AddStore from "../islands/AddStore.tsx";
import AddUser from "../islands/AddUser.tsx";
import UpdatePassword from "../islands/UpdatePassword.tsx";
import UserList from "../islands/UserList.tsx";
import { getStores } from "../utils/storeConfig.ts";
import StoreList from "../islands/StoreList.tsx";

interface SettingsData {
  stores: Array<{
    name: string;
    url: string;
    port: number;
  }>;
}

export const handler: Handlers<SettingsData> = withAuth({
  async GET(req, ctx) {
    const stores = await getStores();
    return ctx.render({ stores });
  },
});

export default function Settings({ data }: PageProps<SettingsData>) {
  return (
    <Layout title="Settings - Event Store Admin">
      <div class="space-y-6">
        <div>
          <h1 class="text-2xl font-bold text-gray-900">Settings</h1>
          <p class="mt-1 text-sm text-gray-500">
            Configure Event Store instances and manage users
          </p>
        </div>

        {/* Event Store Configuration */}
        <div class="bg-white shadow rounded-lg">
          <div class="px-4 py-5 sm:p-6">
            <h3 class="text-lg leading-6 font-medium text-gray-900 mb-4">
              Event Store Instances
            </h3>
            <StoreList />
          </div>
        </div>

        {/* User Management */}
        <div class="bg-white shadow rounded-lg">
          <div class="px-4 py-5 sm:p-6">
            <div class="flex items-center justify-between mb-4">
              <h3 class="text-lg leading-6 font-medium text-gray-900">
                User Management
              </h3>
              <AddUser />
            </div>
            
            <div class="space-y-4">
              {/* Current User Password Update */}
              <div class="border border-gray-200 rounded-lg p-4">
                <div class="flex items-center justify-between">
                  <div>
                    <h4 class="text-sm font-medium text-gray-900">Current User: admin</h4>
                    <p class="text-sm text-gray-500">Update your password</p>
                  </div>
                  <UpdatePassword username="admin" />
                </div>
              </div>
              
              {/* User List */}
              <div>
                <h4 class="text-sm font-medium text-gray-900 mb-3">All Users</h4>
                <UserList />
              </div>
            </div>
          </div>
        </div>

        {/* System Information */}
        <div class="bg-white shadow rounded-lg">
          <div class="px-4 py-5 sm:p-6">
            <h3 class="text-lg leading-6 font-medium text-gray-900 mb-4">
              System Information
            </h3>
            
            <dl class="grid grid-cols-1 gap-x-4 gap-y-6 sm:grid-cols-2">
              <div>
                <dt class="text-sm font-medium text-gray-500">Admin UI Version</dt>
                <dd class="mt-1 text-sm text-gray-900">1.0.0</dd>
              </div>
              <div>
                <dt class="text-sm font-medium text-gray-500">Deno Version</dt>
                <dd class="mt-1 text-sm text-gray-900">2.0.0</dd>
              </div>
              <div>
                <dt class="text-sm font-medium text-gray-500">Fresh Version</dt>
                <dd class="mt-1 text-sm text-gray-900">1.7.3</dd>
              </div>
              <div>
                <dt class="text-sm font-medium text-gray-500">Uptime</dt>
                <dd class="mt-1 text-sm text-gray-900">2 hours, 15 minutes</dd>
              </div>
            </dl>
          </div>
        </div>
      </div>
    </Layout>
  );
} 