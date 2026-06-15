<script>
  import { querySleep } from '@sosweetham/tauri-plugin-health-api';

  let { range } = $props();

  let sessions = $state([]);
  let error = $state('');

  const STAGE_COLORS = {
    awake: '#f9a825',
    light: '#64b5f6',
    deep: '#1e57c8',
    rem: '#9575cd',
    inBed: '#90a4ae',
    asleep: '#4f8cc9',
    outOfBed: '#e57373',
    unknown: '#777',
  };

  $effect(() => {
    const { start, end } = range;
    error = '';
    querySleep({ start, end })
      .then((response) => (sessions = response.sessions))
      .catch((e) => {
        sessions = [];
        error = String(e);
      });
  });

  function fmtTime(ms) {
    return new Date(ms).toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  function hours(session) {
    return ((session.end - session.start) / 3_600_000).toFixed(1);
  }
</script>

<div class="card">
  <h2>Sleep</h2>
  {#if error}
    <p class="error">{error}</p>
  {:else if sessions.length === 0}
    <p class="muted">No sleep sessions in range.</p>
  {:else}
    {#each sessions as session (session.start)}
      <div class="sleep-session">
        <p class="muted">
          {fmtTime(session.start)} → {fmtTime(session.end)} · {hours(session)}h
          {#if session.source}· {session.source}{/if}
        </p>
        <div class="sleep-track">
          {#each session.stages as stage (stage.start)}
            <div
              class="sleep-stage"
              style:left={`${((stage.start - session.start) / (session.end - session.start)) * 100}%`}
              style:width={`${((stage.end - stage.start) / (session.end - session.start)) * 100}%`}
              style:background={STAGE_COLORS[stage.stage] ?? STAGE_COLORS.unknown}
              title={`${stage.stage}: ${fmtTime(stage.start)} → ${fmtTime(stage.end)}`}
            ></div>
          {/each}
        </div>
      </div>
    {/each}
    <p class="legend">
      {#each Object.entries(STAGE_COLORS) as [stage, color] (stage)}
        <span class="legend-item">
          <span class="swatch" style:background={color}></span>{stage}
        </span>
      {/each}
    </p>
  {/if}
</div>
