import React from 'react';
import { useStats, useSyncMutation } from '../hooks';
import { Spinner } from './Spinner';
import { Button } from './Button';

export function Dashboard() {
  const { data: stats, isLoading, error, refetch } = useStats();
  const syncMutation = useSyncMutation();

  if (isLoading) {
    return (
      <div className="dashboard">
        <div className="loading-center">
          <Spinner size={32} />
          <p>Loading workout data...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="dashboard">
        <div className="error-card">
          <h3>Failed to load stats</h3>
          <p>{error instanceof Error ? error.message : 'Unknown error'}</p>
          <Button onClick={() => refetch()}>Retry</Button>
        </div>
      </div>
    );
  }

  const garmin = stats?.latestGarmin;

  return (
    <div className="dashboard">
      {/* Sync Controls */}
      <div className="dash-header">
        <h2>Workout Dashboard</h2>
        <div className="sync-controls">
          <Button
            variant="outline"
            loading={syncMutation.isPending}
            onClick={() => syncMutation.mutate('strava')}
          >
            Sync Strava
          </Button>
          <Button
            variant="outline"
            loading={syncMutation.isPending}
            onClick={() => syncMutation.mutate('garmin')}
          >
            Sync Garmin
          </Button>
          <Button
            variant="ghost"
            onClick={() => refetch()}
          >
            Refresh
          </Button>
        </div>
      </div>

      {/* Overview Cards */}
      <div className="stat-grid">
        <StatCard label="Total Workouts" value={stats?.totalWorkouts ?? 0} icon="&#127939;" />
        <StatCard
          label="Activity Types"
          value={stats?.activityTypes?.length ?? 0}
          icon="&#127942;"
        />
        <StatCard
          label="First Activity"
          value={stats?.earliestDate ? formatDate(stats.earliestDate) : '-'}
          icon="&#128197;"
          small
        />
        <StatCard
          label="Latest Activity"
          value={stats?.latestDate ? formatDate(stats.latestDate) : '-'}
          icon="&#128197;"
          small
        />
      </div>

      {/* Garmin Health Metrics */}
      {garmin && (
        <div className="dash-section">
          <h3 className="section-heading">Garmin Health ({garmin.date})</h3>
          <div className="stat-grid">
            {garmin.bodyBatteryMax != null && (
              <StatCard label="Body Battery" value={garmin.bodyBatteryMax} icon="&#9889;" />
            )}
            {garmin.restingHR != null && (
              <StatCard label="Resting HR" value={`${garmin.restingHR} bpm`} icon="&#10084;" />
            )}
            {garmin.vo2Max != null && (
              <StatCard label="VO2 Max" value={garmin.vo2Max} icon="&#129697;" />
            )}
            {garmin.sleepScore != null && (
              <StatCard label="Sleep Score" value={garmin.sleepScore} icon="&#128564;" />
            )}
            {garmin.weight != null && (
              <StatCard label="Weight" value={`${garmin.weight} kg`} icon="&#9878;" />
            )}
          </div>
        </div>
      )}

      {/* Activity Breakdown */}
      {stats?.summaryByType && stats.summaryByType.length > 0 && (
        <div className="dash-section">
          <h3 className="section-heading">Activity Breakdown</h3>
          <div className="activity-table">
            <div className="table-header">
              <span>Type</span>
              <span>Count</span>
              <span>Distance</span>
              <span>Time</span>
            </div>
            {stats.summaryByType.map((s) => (
              <div key={s.type} className="table-row">
                <span className="type-name">{s.type}</span>
                <span>{s.count}</span>
                <span>{s.totalDistanceKm != null ? `${s.totalDistanceKm} km` : '-'}</span>
                <span>{s.totalHours != null ? `${s.totalHours} h` : '-'}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Activity Type Pills */}
      {stats?.activityTypes && stats.activityTypes.length > 0 && (
        <div className="dash-section">
          <h3 className="section-heading">Activity Types</h3>
          <div className="type-pills">
            {stats.activityTypes.map((t) => (
              <span key={t} className="pill">{t}</span>
            ))}
          </div>
        </div>
      )}

      {syncMutation.isSuccess && (
        <div className="toast toast-success" onClick={() => syncMutation.reset()}>
          Sync completed successfully. Click to dismiss.
        </div>
      )}
      {syncMutation.isError && (
        <div className="toast toast-error" onClick={() => syncMutation.reset()}>
          Sync failed: {syncMutation.error instanceof Error ? syncMutation.error.message : 'Unknown error'}
        </div>
      )}
    </div>
  );
}

function StatCard({
  label,
  value,
  icon,
  small,
}: {
  label: string;
  value: string | number;
  icon: string;
  small?: boolean;
}) {
  return (
    <div className="stat-card">
      <div className="stat-icon">{icon}</div>
      <div className="stat-info">
        <div className={`stat-value ${small ? 'stat-value-sm' : ''}`}>{value}</div>
        <div className="stat-label">{label}</div>
      </div>
    </div>
  );
}

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  } catch {
    return iso;
  }
}
