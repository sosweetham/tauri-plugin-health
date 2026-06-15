<script>
  import { queryAggregated } from '@sosweetham/tauri-plugin-health-api';

  let { title, metric, range } = $props();

  let buckets = $state([]);
  let error = $state('');
  let loading = $state(false);

  $effect(() => {
    const { start, end, bucket } = range;
    loading = true;
    error = '';
    queryAggregated({ metric, start, end, bucket })
      .then((response) => (buckets = response.buckets))
      .catch((e) => {
        buckets = [];
        error = String(e);
      })
      .finally(() => (loading = false));
  });

  const total = $derived(buckets.reduce((sum, b) => sum + b.value, 0));
  const max = $derived(Math.max(1, ...buckets.map((b) => b.value)));
  const unit = $derived(buckets[0]?.unit ?? '');
  const isAvg = $derived(metric === 'heartRate');

  function fmt(value) {
    return value >= 100 ? Math.round(value).toLocaleString() : value.toFixed(1);
  }
</script>

<div class="card">
  <h2>
    {title}
    <span class="total">
      {#if buckets.length > 0}
        {isAvg
          ? `${fmt(total / buckets.length)} avg ${unit}`
          : `${fmt(total)} ${unit}`}
      {/if}
    </span>
  </h2>
  {#if loading}
    <p class="muted">Loading…</p>
  {:else if error}
    <p class="error">{error}</p>
  {:else if buckets.length === 0}
    <p class="muted">No data in range.</p>
  {:else}
    <div class="bars">
      {#each buckets as bucket (bucket.start)}
        <div
          class="bar"
          style:height={`${(bucket.value / max) * 100}%`}
          title={`${new Date(bucket.start).toLocaleString()}: ${fmt(bucket.value)} ${bucket.unit}`}
        ></div>
      {/each}
    </div>
  {/if}
</div>
