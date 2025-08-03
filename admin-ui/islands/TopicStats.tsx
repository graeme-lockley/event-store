import { useEffect, useState } from "preact/hooks";

interface TopicStatsProps {
  store: string;
  topic: string;
}

export default function TopicStats({ store, topic }: TopicStatsProps) {
  const [stats, setStats] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    fetch(
      `/api/topic-stats?store=${encodeURIComponent(store)}&topic=${
        encodeURIComponent(topic)
      }`,
    )
      .then((res) => res.json())
      .then((data) => {
        if (data.error) setError(data.error);
        else setStats(data);
        setLoading(false);
      })
      .catch((e) => {
        setError(e.message);
        setLoading(false);
      });
  }, [store, topic]);

  if (loading) {
    return <div class="p-4 text-gray-500">Loading topic statistics...</div>;
  }
  if (error) return <div class="p-4 text-red-600">Error: {error}</div>;
  if (!stats) return null;

  return (
    <div class="space-y-4">
      <div class="flex flex-wrap gap-6">
        <div class="bg-white rounded shadow p-4 flex-1 min-w-[180px]">
          <div class="text-xs text-gray-500">Total Events</div>
          <div class="text-2xl font-bold">{stats.totalEvents}</div>
        </div>
        <div class="bg-white rounded shadow p-4 flex-1 min-w-[180px]">
          <div class="text-xs text-gray-500">Consumers</div>
          <div class="text-2xl font-bold">{stats.consumerCount}</div>
        </div>
        <div class="bg-white rounded shadow p-4 flex-1 min-w-[180px]">
          <div class="text-xs text-gray-500">Storage Usage</div>
          <div class="text-2xl font-bold">
            {(stats.storageBytes / 1024).toFixed(1)} KB
          </div>
        </div>
      </div>
      <div class="bg-white rounded shadow p-4">
        <div class="font-semibold mb-2">Events per Day (last 30 days)</div>
        <div class="overflow-x-auto">
          <table class="min-w-full text-xs">
            <thead>
              <tr>
                <th class="px-2 py-1 text-left text-gray-500">Date</th>
                <th class="px-2 py-1 text-right text-gray-500">Count</th>
              </tr>
            </thead>
            <tbody>
              {Object.entries(stats.perDay).slice(-30).map(([date, count]) => (
                <tr key={date}>
                  <td class="px-2 py-1">{date}</td>
                  <td class="px-2 py-1 text-right">{count as any}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
      <div class="bg-white rounded shadow p-4">
        <div class="font-semibold mb-2">Events per Month</div>
        <div class="overflow-x-auto">
          <table class="min-w-full text-xs">
            <thead>
              <tr>
                <th class="px-2 py-1 text-left text-gray-500">Month</th>
                <th class="px-2 py-1 text-right text-gray-500">Count</th>
              </tr>
            </thead>
            <tbody>
              {Object.entries(stats.perMonth).map(([month, count]) => (
                <tr key={month}>
                  <td class="px-2 py-1">{month}</td>
                  <td class="px-2 py-1 text-right">{count as any}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
