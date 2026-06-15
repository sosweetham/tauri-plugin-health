<script>
  import { onMount } from 'svelte';
  import {
    isAvailable,
    requestPermissions,
    checkPermissions,
    openSettings,
  } from '@sosweetham/tauri-plugin-health-api';
  import MetricCard from './lib/MetricCard.svelte';
  import SleepTimeline from './lib/SleepTimeline.svelte';
  import WorkoutsList from './lib/WorkoutsList.svelte';

  const ALL_METRICS = [
    'steps',
    'distance',
    'activeCalories',
    'totalCalories',
    'heartRate',
    'restingHeartRate',
    'heartRateVariability',
    'workouts',
    'sleep',
  ];

  let availability = $state(null);
  let permissions = $state(null);
  let permissionError = $state('');

  /** 'today' | '7d' | '30d' */
  let preset = $state('7d');

  const range = $derived.by(() => {
    const todayStart = new Date();
    todayStart.setHours(0, 0, 0, 0);
    const dayMs = 24 * 60 * 60 * 1000;
    const end = Date.now();
    if (preset === 'today') {
      return { start: todayStart.getTime(), end, bucket: 'hour' };
    }
    const days = preset === '7d' ? 7 : 30;
    return { start: todayStart.getTime() - (days - 1) * dayMs, end, bucket: 'day' };
  });

  onMount(async () => {
    try {
      availability = await isAvailable();
      if (availability.available) {
        permissions = await checkPermissions().catch(() => null);
      }
    } catch (e) {
      permissionError = String(e);
    }
  });

  async function askPermissions() {
    permissionError = '';
    try {
      permissions = await requestPermissions({ read: ALL_METRICS });
    } catch (e) {
      permissionError = String(e);
    }
  }
</script>

<main>
  <h1>Health Demo</h1>

  {#if availability === null}
    <div class="card muted">Checking availability…</div>
  {:else if !availability.available}
    <div class="card banner warn">
      {#if availability.reason === 'providerUpdateRequired'}
        Health Connect needs an update.
        <button onclick={() => openSettings()}>Open settings</button>
      {:else if availability.platform === 'unsupported'}
        Health data is only available on iOS and Android. ({availability.reason})
      {:else}
        Health data unavailable: {availability.reason ?? 'unknown reason'}
      {/if}
    </div>
  {:else}
    <div class="card banner ok">
      {availability.platform === 'ios' ? 'HealthKit' : 'Health Connect'} ready
    </div>

    <div class="card">
      <h2>Permissions</h2>
      <button onclick={askPermissions}>Request all permissions</button>
      <button onclick={() => openSettings()}>Open settings</button>
      {#if permissions}
        <p>
          {#each permissions.granted as metric (metric)}
            <span class="chip">{metric}</span>
          {/each}
          <span class="chip state">
            {permissions.state === 'exact' ? 'exact' : 'best effort (iOS)'}
          </span>
        </p>
      {/if}
      {#if permissionError}
        <p class="error">{permissionError}</p>
      {/if}
    </div>

    <div class="card presets">
      {#each ['today', '7d', '30d'] as p (p)}
        <button class:active={preset === p} onclick={() => (preset = p)}>
          {p === 'today' ? 'Today' : p}
        </button>
      {/each}
    </div>

    <MetricCard title="Steps" metric="steps" {range} />
    <MetricCard title="Distance" metric="distance" {range} />
    <MetricCard title="Active calories" metric="activeCalories" {range} />
    <MetricCard title="Total calories" metric="totalCalories" {range} />
    <MetricCard title="Heart rate" metric="heartRate" {range} />
    <MetricCard title="Resting heart rate" metric="restingHeartRate" {range} />
    <MetricCard title="HRV" metric="heartRateVariability" {range} />
    <SleepTimeline {range} />
    <WorkoutsList {range} />
  {/if}
</main>
