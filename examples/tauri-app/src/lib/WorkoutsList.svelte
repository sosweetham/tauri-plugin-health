<script>
  import { queryWorkouts } from '@sosweetham/tauri-plugin-health-api';

  let { range } = $props();

  let workouts = $state([]);
  let error = $state('');

  $effect(() => {
    const { start, end } = range;
    error = '';
    queryWorkouts({ start, end })
      .then((response) => (workouts = response.workouts))
      .catch((e) => {
        workouts = [];
        error = String(e);
      });
  });

  function fmtDate(ms) {
    return new Date(ms).toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  function fmtDuration(sec) {
    const minutes = Math.round(sec / 60);
    return minutes >= 60
      ? `${Math.floor(minutes / 60)}h ${minutes % 60}m`
      : `${minutes}m`;
  }
</script>

<div class="card">
  <h2>Workouts</h2>
  {#if error}
    <p class="error">{error}</p>
  {:else if workouts.length === 0}
    <p class="muted">No workouts in range.</p>
  {:else}
    {#each workouts as workout (workout.start)}
      <div class="workout">
        <strong>{workout.activityType}</strong>
        <span class="muted">
          {fmtDate(workout.start)} · {fmtDuration(workout.durationSec)}
          {#if workout.calories}· {Math.round(workout.calories)} kcal{/if}
          {#if workout.distanceMeters}
            · {(workout.distanceMeters / 1000).toFixed(2)} km
          {/if}
          {#if workout.source}· {workout.source}{/if}
        </span>
      </div>
    {/each}
  {/if}
</div>
