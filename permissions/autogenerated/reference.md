## Default Permission

Default permissions for the health plugin.

#### Granted Permissions

- Availability + OS-level permission management (`is_available`,
  `request_permissions`, `check_permissions`, `open_settings`).
- Read-only health queries (`query_aggregated`, `query_sleep`,
  `query_workouts`, `query_heart_rate_samples`).

Note: these are Tauri IPC permissions. Actual access to health data is
additionally gated by the OS (HealthKit authorization on iOS, Health
Connect permission grants on Android).

#### This default permission set includes the following:

- `allow-is-available`
- `allow-request-permissions`
- `allow-check-permissions`
- `allow-query-aggregated`
- `allow-query-sleep`
- `allow-query-workouts`
- `allow-query-heart-rate-samples`
- `allow-open-settings`

## Permission Table

<table>
<tr>
<th>Identifier</th>
<th>Description</th>
</tr>


<tr>
<td>

`health:allow-check-permissions`

</td>
<td>

Enables the check_permissions command without any pre-configured scope.

</td>
</tr>

<tr>
<td>

`health:deny-check-permissions`

</td>
<td>

Denies the check_permissions command without any pre-configured scope.

</td>
</tr>

<tr>
<td>

`health:allow-is-available`

</td>
<td>

Enables the is_available command without any pre-configured scope.

</td>
</tr>

<tr>
<td>

`health:deny-is-available`

</td>
<td>

Denies the is_available command without any pre-configured scope.

</td>
</tr>

<tr>
<td>

`health:allow-open-settings`

</td>
<td>

Enables the open_settings command without any pre-configured scope.

</td>
</tr>

<tr>
<td>

`health:deny-open-settings`

</td>
<td>

Denies the open_settings command without any pre-configured scope.

</td>
</tr>

<tr>
<td>

`health:allow-query-aggregated`

</td>
<td>

Enables the query_aggregated command without any pre-configured scope.

</td>
</tr>

<tr>
<td>

`health:deny-query-aggregated`

</td>
<td>

Denies the query_aggregated command without any pre-configured scope.

</td>
</tr>

<tr>
<td>

`health:allow-query-heart-rate-samples`

</td>
<td>

Enables the query_heart_rate_samples command without any pre-configured scope.

</td>
</tr>

<tr>
<td>

`health:deny-query-heart-rate-samples`

</td>
<td>

Denies the query_heart_rate_samples command without any pre-configured scope.

</td>
</tr>

<tr>
<td>

`health:allow-query-sleep`

</td>
<td>

Enables the query_sleep command without any pre-configured scope.

</td>
</tr>

<tr>
<td>

`health:deny-query-sleep`

</td>
<td>

Denies the query_sleep command without any pre-configured scope.

</td>
</tr>

<tr>
<td>

`health:allow-query-workouts`

</td>
<td>

Enables the query_workouts command without any pre-configured scope.

</td>
</tr>

<tr>
<td>

`health:deny-query-workouts`

</td>
<td>

Denies the query_workouts command without any pre-configured scope.

</td>
</tr>

<tr>
<td>

`health:allow-request-permissions`

</td>
<td>

Enables the request_permissions command without any pre-configured scope.

</td>
</tr>

<tr>
<td>

`health:deny-request-permissions`

</td>
<td>

Denies the request_permissions command without any pre-configured scope.

</td>
</tr>
</table>
