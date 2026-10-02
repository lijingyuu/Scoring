<template>
  <div class="app-shell">
    <header class="app-header">
      <RouterLink class="brand-link" to="/lobby">
        <strong>Eunomia</strong>
      </RouterLink>
      <nav class="app-nav">
        <RouterLink to="/lobby">赛事大厅</RouterLink>
        <RouterLink to="/create">创建比赛</RouterLink>
      </nav>
      <div class="account-area">
        <ThemeSwitcher />
        <div class="user-pill">{{ profile?.nickname || '工作台用户' }}</div>
        <button class="ghost-action small" @click="logout">退出登录</button>
      </div>
    </header>

    <main class="content">
      <p v-if="success" class="success-text">{{ success }}</p>
      <!-- §9-5：未完善资料的新用户在提交时必被后端拒——进入页面就给出引导，避免填完整表单才失败 -->
      <p v-if="profile && !profile.profileCompleted" class="profile-incomplete-banner">
        你的账号还未完善资料：请先打开微信小程序「我的」页设置昵称与头像，完成后再提交创建。
      </p>

      <div class="create-layout" :class="{ 'has-side-panel': showPlayerSidePanel, 'has-team-side-panel': showTeamSidePanel }">
        <div class="create-main">
          <section class="form-grid">
            <div class="panel">
              <h2>基础信息</h2>
              <div class="field-grid tournament-basic-grid">
                <label>
                  <span>赛事名称</span>
                  <input v-model.trim="form.name" placeholder="例如 2026 春季赛" />
                </label>
                <label>
                  <span>地点</span>
                  <input v-model.trim="form.location" placeholder="可选" />
                </label>
               <label>
                 <span>裁判密码</span>
                 <input v-model.trim="form.refereePassword" maxlength="16" placeholder="不少于8位数字，可选" />
               </label>
               <label>
                 <span>运动</span>
                 <select v-model.number="form.sportType" @change="syncSportDefaults">
                    <option :value="0">羽毛球</option>
                    <option :value="1">排球</option>
                  </select>
                </label>
                <label v-if="form.sportType === 0">
                  <span>参赛形式</span>
                  <select v-model.number="form.participantType" @change="syncParticipantDefaults">
                    <option :value="0">个人赛</option>
                    <option :value="1">团体赛</option>
                  </select>
                </label>
                <label v-if="form.sportType === 0 && form.participantType === 0" class="division-toggle-field">
                  <span>多组别</span>
                  <span class="inline-toggle division-toggle">
                    <input type="checkbox" :checked="divisionsEnabled" @change="toggleDivisionsEnabled" />
                    <span>同一赛事分男单/女单等多个组别</span>
                  </span>
                </label>
                <label v-if="isBadmintonTeam">
                  <span>团体模板</span>
                  <select v-model.number="form.teamMatchTemplate" @change="syncTemplateDefaults">
                    <option :value="1">苏迪曼杯 5 项</option>
                    <option :value="2">接力追分赛</option>
                  </select>
                </label>
              </div>
            </div>

            <div v-if="!divisionMode" class="panel rule-config-panel">
              <h2>赛制与规则</h2>
              <div class="field-grid three">
                <label class="rule-type-field">
                  <span>赛制</span>
                  <select v-model.number="form.tournamentType">
                    <option :value="0">淘汰赛</option>
                    <option :value="1">小组 + 淘汰</option>
                    <option :value="2">循环赛</option>
                  </select>
                </label>
                <label v-if="form.tournamentType === 0" class="rule-round-field">
                  <span>淘汰轮数</span>
                  <div class="input-with-hint knockout-round-input">
                    <input v-model.number="form.knockoutRounds" type="number" min="1" max="10" />
                    <span class="input-suffix">轮</span>
                    <small>{{ knockoutParticipantRangeText }}</small>
                    <span class="knockout-round-stepper">
                      <button type="button" aria-label="增加淘汰轮数" @click="adjustKnockoutRounds(1)">▲</button>
                      <button type="button" aria-label="减少淘汰轮数" @click="adjustKnockoutRounds(-1)">▼</button>
                    </span>
                  </div>
                </label>
                <label v-if="form.tournamentType === 0 && !divisionMode" class="manual-draw-toggle-field">
                  <span>手写签表</span>
                  <span class="inline-toggle">
                    <input
                      type="checkbox"
                      :checked="manualDrawEnabled"
                      :disabled="!canEnableManualDraw"
                      @change="toggleManualDraw"
                    />
                    <span>手动安排签位（默认自动抽签）</span>
                  </span>
                  <small v-if="manualDrawUnavailableReason" class="muted">{{ manualDrawUnavailableReason }}</small>
                </label>
                <label v-if="form.tournamentType === 1 && !divisionMode" class="manual-groups-toggle-field">
                  <span>手写分组</span>
                  <span class="inline-toggle">
                    <input
                      type="checkbox"
                      :checked="manualGroupsEnabled"
                      :disabled="!canEnableManualGroups"
                      @change="toggleManualGroups"
                    />
                    <span>手动安排小组名单（默认自动分组）</span>
                  </span>
                  <small v-if="manualGroupsUnavailableReason" class="muted">{{ manualGroupsUnavailableReason }}</small>
                </label>
                <label v-if="form.tournamentType === 1">
                  <span>淘汰名额</span>
                  <select v-model.number="form.knockoutSlots">
                    <option :value="4">4</option>
                    <option :value="8">8</option>
                    <option :value="16">16</option>
                  </select>
                </label>
                <label v-if="form.tournamentType === 1">
                  <span>每组出线</span>
                  <select v-model.number="form.qualifiersPerGroup">
                    <option :value="1">1</option>
                    <option :value="2">2</option>
                  </select>
                </label>
                <label v-if="form.tournamentType === 2">
                  <span>循环轮次</span>
                  <select v-model.number="form.roundRobinRounds">
                    <option :value="1">单循环</option>
                    <option :value="2">双循环</option>
                  </select>
                </label>
                <label v-if="form.tournamentType !== 2">
                  <span>季军赛</span>
                  <select v-model="form.thirdPlaceEnabled" :disabled="!canEnableThirdPlace">
                    <option :value="false">不需要</option>
                    <option :value="true">需要</option>
                  </select>
                  <small v-if="!canEnableThirdPlace">淘汰阶段至少 4 个参赛方</small>
                </label>
              </div>

              <div v-if="showRankingConfig" class="ranking-template-panel">
                <label>
                  <span>小组赛排名规则</span>
                  <select v-model="form.rankingTemplate">
                    <option
                      v-for="option in rankingTemplateOptions"
                      :key="option.value"
                      :value="option.value"
                    >
                      {{ option.name }}
                    </option>
                  </select>
                </label>
                <p>{{ selectedRankingTemplateDesc }}</p>
              </div>

              <div class="field-grid four base-rule-grid" :class="{ 'is-overridden': baseRuleOverridden }">
                <label>
                  <span>局数</span>
                  <select v-model.number="form.rule.bestOf" :disabled="isRelay || baseRuleOverridden" @change="setBestOf(form.rule, form.rule.bestOf)">
                    <option :value="1">一局</option>
                    <option :value="3">三局两胜</option>
                    <option :value="5">五局三胜</option>
                  </select>
                </label>
                <label>
                  <span>{{ isRelay ? '分段基准分' : '基础胜分' }}</span>
                  <input v-model.number="form.rule.pointsToWin" type="number" min="1" :disabled="baseRuleOverridden" />
                </label>
                <label>
                  <span>追分</span>
                  <select v-model="form.rule.enableDeuce" :disabled="isRelay || baseRuleOverridden">
                    <option :value="true">开启</option>
                    <option :value="false">关闭</option>
                  </select>
                </label>
                <label class="rule-cap-field">
                  <span>{{ isRelay ? '轮转人数' : '封顶分' }}</span>
                  <input v-model.number="form.rule.capPoint" type="number" min="1" :disabled="baseRuleOverridden" />
                </label>
                <label v-if="isVolleyball">
                  <span>决胜局胜分</span>
                  <input v-model.number="form.rule.decidingPointsToWin" type="number" min="1" :disabled="baseRuleOverridden" />
                </label>
              </div>

              <div v-if="supportsRoundRules" class="round-rule-panel">
                <label class="inline-toggle">
                  <input v-model="form.roundRuleEnabled" type="checkbox" @change="syncRoundRules" />
                  <span>启用分段规则设计</span>
                </label>
                <div v-if="form.roundRuleEnabled" class="round-rule-entry">
                  <button class="secondary-action small" type="button" @click="openRoundRuleDrawer">
                    设计分段规则
                  </button>
                  <div class="round-rule-summary">
                    <span v-for="segment in activeRoundRuleSegments" :key="segment.id">
                      {{ segment.name || '未命名赛段' }}：{{ formatSegmentScopes(segment) }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </section>

          <section v-if="divisionMode" class="panel divisions-panel">
            <div class="panel-head">
              <h2>组别设置</h2>
              <span class="muted">{{ divisionDrafts.length }} 个组别</span>
            </div>
            <div v-for="(d, dIndex) in divisionDrafts" :key="d.localId" class="division-card">
              <div class="division-card-head">
                <span class="division-index">组别 {{ dIndex + 1 }}</span>
                <input v-model.trim="d.name" class="division-name-input" maxlength="64" placeholder="例如 男单组" />
                <button
                  v-if="divisionDrafts.length > 2"
                  class="text-action danger"
                  type="button"
                  @click="removeDivisionDraft(dIndex)"
                >
                  删除组别
                </button>
              </div>
              <div class="field-grid three division-format-grid">
                <label>
                  <span>赛制</span>
                  <select v-model.number="d.tournamentType" @change="onDivisionTypeChange(d)">
                    <option :value="0">淘汰赛</option>
                    <option :value="1">小组赛 + 淘汰赛</option>
                    <option :value="2">循环赛</option>
                  </select>
                </label>
                <label v-if="d.tournamentType === 0">
                  <span>淘汰轮数</span>
                  <input v-model.number="d.knockoutRounds" type="number" min="1" max="10" />
                  <small class="muted">该轮数需要 {{ divisionRoundsHint(d.knockoutRounds) }} 名选手</small>
                </label>
                <template v-if="d.tournamentType === 1">
                  <label>
                    <span>淘汰名额</span>
                    <select v-model.number="d.knockoutSlots">
                      <option :value="2">2</option>
                      <option :value="4">4</option>
                      <option :value="8">8</option>
                      <option :value="16">16</option>
                      <option :value="32">32</option>
                    </select>
                  </label>
                  <label>
                    <span>每组出线</span>
                    <select v-model.number="d.qualifiersPerGroup">
                      <option :value="1">1</option>
                      <option :value="2">2</option>
                    </select>
                  </label>
                </template>
                <label v-if="d.tournamentType === 2">
                  <span>轮次</span>
                  <select v-model.number="d.roundRobinRounds">
                    <option :value="1">单循环</option>
                    <option :value="2">双循环</option>
                  </select>
                </label>
                <label v-if="d.tournamentType !== 2" class="inline-toggle division-third-place">
                  <input v-model="d.thirdPlaceEnabled" type="checkbox" />
                  <span>季军赛</span>
                </label>
              </div>
              <div class="field-grid four division-rule-grid">
                <label>
                  <span>总局数</span>
                  <select v-model.number="d.rule.bestOf" @change="setBestOf(d.rule, d.rule.bestOf)">
                    <option :value="1">一局</option>
                    <option :value="3">三局两胜</option>
                    <option :value="5">五局三胜</option>
                  </select>
                </label>
                <label>
                  <span>胜局</span>
                  <input :value="Math.floor(Number(d.rule.bestOf) / 2) + 1" type="number" readonly />
                </label>
                <label>
                  <span>每局分</span>
                  <input v-model.number="d.rule.pointsToWin" type="number" min="1" />
                </label>
                <label>
                  <span>追分</span>
                  <select v-model="d.rule.enableDeuce">
                    <option :value="true">开启</option>
                    <option :value="false">关闭</option>
                  </select>
                </label>
                <label>
                  <span>封顶</span>
                  <input v-model.number="d.rule.capPoint" type="number" min="1" @change="clampDivisionCapPoint(d)" />
                </label>
              </div>
              <div v-if="d.tournamentType === 1" class="division-knockout-rule">
                <p class="division-knockout-rule-title">淘汰赛规则</p>
                <div class="field-grid four division-rule-grid">
                  <label>
                    <span>总局数</span>
                    <select v-model.number="d.knockoutRule.bestOf">
                      <option :value="1">一局</option>
                      <option :value="3">三局两胜</option>
                      <option :value="5">五局三胜</option>
                    </select>
                  </label>
                  <label>
                    <span>胜局</span>
                    <input :value="Math.floor(Number(d.knockoutRule.bestOf) / 2) + 1" type="number" readonly />
                  </label>
                  <label>
                    <span>每局分</span>
                    <input v-model.number="d.knockoutRule.pointsToWin" type="number" min="1" />
                  </label>
                  <label>
                    <span>追分</span>
                    <select v-model="d.knockoutRule.enableDeuce">
                      <option :value="true">开启</option>
                      <option :value="false">关闭</option>
                    </select>
                  </label>
                  <label>
                    <span>封顶</span>
                    <input v-model.number="d.knockoutRule.capPoint" type="number" min="1" />
                  </label>
                </div>
              </div>
              <label class="division-players-label">
                <span>选手名单</span>
                <textarea
                  v-model="d.playersText"
                  placeholder="每行一名选手，可在前面加种子序号，例如：1 张三"
                ></textarea>
                <small class="muted">已识别 {{ countDivisionPlayers(d.playersText) }} 名选手</small>
              </label>
            </div>
            <button
              class="ghost-action small"
              type="button"
              :disabled="divisionDrafts.length >= 16"
              @click="addDivisionDraft"
            >
              添加组别
            </button>
            <div class="ranking-template-panel division-ranking-panel">
              <label>
                <span>排名规则</span>
                <select v-model="divisionRankingTemplate">
                  <option
                    v-for="option in divisionRankingTemplateOptions"
                    :key="option.value"
                    :value="option.value"
                  >
                    {{ option.name }}
                  </option>
                </select>
              </label>
              <p>对小组赛/循环赛组别生效，纯淘汰组别忽略</p>
            </div>
          </section>

          <section v-if="isIndividual && !divisionMode" class="panel player-paste-panel">
            <div class="panel-head">
              <h2>选手名单粘贴板</h2>
              <span class="muted">{{ players.length }} 人</span>
            </div>
            <textarea
              v-model="playerPaste"
              class="player-paste-input"
              placeholder="每行一名选手。行首数字表示种子，可写 1张三、1 张三、1.张三、1、张三；不写数字则种子为空。"
            ></textarea>
            <div class="player-paste-actions">
              <button class="secondary-action" @click="applyPlayersPaste">生成选手列表</button>
              <button class="secondary-action match-submit-action" :disabled="submitting" @click="onSubmitClick">
                {{ submitting ? '创建中...' : '生成比赛' }}
              </button>
            </div>
          </section>

          <section v-else class="panel team-paste-panel">
            <div class="panel-head">
              <h2>队伍队员名单粘贴板</h2>
              <span class="muted">{{ teams.length }} 队</span>
            </div>
            <div class="quick-team-form">
              <label>
                <span>队名</span>
                <input v-model.trim="quickTeamName" placeholder="例如 一队" />
              </label>
              <label>
                <span>队员名单</span>
                <textarea
                  v-model="teamPaste"
                  :placeholder="isVolleyball ? '每行一名队员，格式：姓名 号码；第一名默认队长' : '每行一名队员，第一名默认队长'"
                ></textarea>
              </label>
              <div class="team-paste-actions">
                <button class="ghost-action" @click="quickAddTeam">快捷添加队伍</button>
                <button class="secondary-action match-submit-action" :disabled="submitting" @click="onSubmitClick">
                  {{ submitting ? '创建中...' : '生成比赛' }}
                </button>
              </div>
            </div>

            <div v-if="teams.length" class="team-list-block">
              <div class="panel-head">
                <h3>队伍列表</h3>
                <span class="muted">{{ teams.length }} 队</span>
              </div>
              <div class="team-list">
                <div class="team-list-item team-list-header">
                  <span>种子序号</span>
                  <span>队名</span>
                  <span></span>
                </div>
                <div
                  v-for="team in teams"
                  :key="team.id"
                  class="team-list-item with-seed"
                  :class="{ active: selectedTeamId === team.id }"
                  @click="selectTeam(team.id)"
                >
                  <label class="team-seed-cell" title="种子序号" @click.stop>
                    <input v-model.number="team.seed" class="team-seed-input" type="number" min="1" placeholder="-" />
                  </label>
                  <span class="team-name-cell">{{ team.name }}</span>
                  <button class="text-action danger" type="button" @click.stop="requestDeleteTeam(team.id)">移除队伍</button>
                </div>
              </div>
            </div>
          </section>

          <section v-if="manualDrawEnabled" ref="manualDrawPanel" class="panel manual-draw-panel" :class="{ 'manual-panel-flash': manualPanelFlash }">
            <div class="panel-head">
              <h2>手写签表</h2>
              <span class="muted">
                容量 {{ manualCapacity }} 个签位 · {{ manualParticipantCount }} 个参赛单位
              </span>
            </div>
            <p class="muted">
              容量取不小于参赛单位数的最小 2 的幂。点击签位选中后，在下方名单面板点名单项填入（自动跳到下一空位），「轮空位」填空签；也可点「剩余随机填入」随机补齐。同一场比赛的两个签位不能都是轮空。
            </p>
            <p v-if="!manualDrawValidation.ok" class="error-text">{{ manualDrawValidation.message }}</p>
            <DrawSlotEditor v-model="manualSlots" :roster="manualRoster" :flash-match="editorFlashMatch" :flash-nonce="editorFlashNonce" />
          </section>

          <section
            v-if="manualGroupsEnabled"
            ref="manualGroupsPanel"
            class="panel manual-draw-panel manual-groups-panel"
            :class="{ 'manual-panel-flash': manualGroupsPanelFlash }"
          >
            <div class="panel-head">
              <h2>手写分组</h2>
              <span class="muted">
                {{ manualGroupCount }} 个小组 · {{ manualParticipantCount }} 个参赛单位 · 每组至少 {{ manualMinPerGroup }} 人
              </span>
            </div>
            <p class="muted">
              小组数量固定为「淘汰名额 ÷ 每组出线」（{{ form.knockoutSlots }} ÷ {{ form.qualifiersPerGroup }}），每组人数不少于 {{ manualMinPerGroup }} 人，组间允许不均。点组标题选中目标组后，在下方名单面板点名单项加入该组末尾（组内顺序即组内座次）；组内成员点 × 移出，也可点「剩余随机分配」随机补人。
            </p>
            <p v-if="!groupCountValid" class="error-text">淘汰名额需能被每组出线整除，请先调整上方「淘汰名额 / 每组出线」。</p>
            <p v-else-if="!manualGroupsValidation.ok" class="error-text">{{ manualGroupsValidation.message }}</p>
            <GroupAssignmentEditor
              v-if="groupCountValid"
              v-model="manualGroups"
              :roster="manualRoster"
              :group-count="manualGroupCount"
              :min-per-group="manualMinPerGroup"
            />
          </section>

        </div>

        <aside v-if="showTeamSidePanel" class="team-side-panel">
          <section class="panel team-detail-panel">
            <div class="team-detail-head">
              <div v-if="editingTeamName" class="team-name-editor">
                <input v-model.trim="teamNameDraft" placeholder="队伍名称" @keyup.enter="confirmTeamNameEdit" />
                <button class="ghost-action small" type="button" @click="confirmTeamNameEdit">确定</button>
              </div>
              <div v-else class="team-title-line">
                <h2>{{ selectedTeam.name }}</h2>
              </div>
              <div class="team-title-actions">
                <button class="tiny-text-action" type="button" @click="startTeamNameEdit">编辑队名</button>
                <button v-if="!changingCaptain" class="tiny-text-action" type="button" @click="startCaptainChange">更改队长</button>
              </div>
            </div>
            <div class="editable-list compact-list team-member-table" :class="{ 'has-jersey': isVolleyball }">
              <div class="row header team-row">
                <span>姓名</span>
                <span v-if="isVolleyball">号码</span>
              </div>
              <div v-for="(member, memberIndex) in selectedTeam.members" :key="memberIndex" class="row team-row">
                <div class="name-cell">
                  <input v-model.trim="member.name" placeholder="成员姓名" />
                  <span v-if="isCaptainMember(selectedTeam, memberIndex)" class="captain-badge">队长</span>
                  <button
                    v-if="changingCaptain"
                    class="captain-select"
                    :class="{ selected: captainCandidateIndex === memberIndex }"
                    type="button"
                    aria-label="选择队长"
                    :disabled="!member.name"
                    @click="captainCandidateIndex = memberIndex"
                  ></button>
                  <button v-else class="icon-remove" type="button" aria-label="删除成员" @click="deleteMember(selectedTeam, memberIndex)"></button>
                </div>
                <input v-if="isVolleyball" v-model.number="member.jerseyNumber" class="jersey-input" type="number" min="1" placeholder="-" />
              </div>
            </div>
            <div class="team-detail-actions">
              <button class="ghost-action small" @click="addTeamMember(selectedTeam)">添加成员</button>
              <button
                v-if="changingCaptain"
                class="secondary-action small"
                type="button"
                :disabled="captainCandidateIndex < 0"
                @click="confirmCaptainChange"
              >
                确定
              </button>
            </div>
          </section>
        </aside>

        <aside v-if="showPlayerSidePanel" class="player-side-panel">
          <section class="panel player-list-panel">
            <div class="panel-head">
              <h2>选手名单</h2>
              <span class="muted">{{ players.length }} 人</span>
            </div>
            <div class="editable-list player-table">
              <div class="row header"><span>种子</span><span>姓名</span></div>
              <div v-for="(player, index) in players" :key="index" class="row">
                <input v-model.number="player.seed" type="number" placeholder="-" />
                <div class="name-cell">
                  <input v-model.trim="player.name" placeholder="选手姓名" />
                  <button class="icon-remove" type="button" aria-label="删除选手" @click="players.splice(index, 1)"></button>
                </div>
              </div>
            </div>
            <button class="ghost-action small" @click="players.push({ name: '', seed: null })">添加选手</button>
          </section>
        </aside>
      </div>
    </main>

    <div v-if="modalError" class="modal-overlay" @click.self="modalError = ''">
      <section class="message-modal">
        <h2>信息不完整</h2>
        <p>{{ modalError }}</p>
        <button class="secondary-action" @click="modalError = ''">知道了</button>
      </section>
    </div>

    <div v-if="manualDrawProblemsOpen" class="modal-overlay" @click.self="manualDrawProblemsOpen = false">
      <section class="message-modal">
        <h2>签表还没填完</h2>
        <p v-for="line in manualDrawProblemLines" :key="line">{{ line }}</p>
        <div class="message-modal-actions">
          <button class="ghost-action" type="button" @click="manualDrawProblemsOpen = false">继续填写</button>
          <button class="secondary-action" type="button" @click="jumpToDrawProblem">去处理</button>
        </div>
      </section>
    </div>

    <div v-if="manualGroupsProblemsOpen" class="modal-overlay" @click.self="manualGroupsProblemsOpen = false">
      <section class="message-modal">
        <h2>分组还没完成</h2>
        <p v-for="line in manualGroupProblemLines" :key="line">{{ line }}</p>
        <div class="message-modal-actions">
          <button class="ghost-action" type="button" @click="manualGroupsProblemsOpen = false">继续调整</button>
          <button class="secondary-action" type="button" @click="jumpToGroupProblem">去处理</button>
        </div>
      </section>
    </div>

    <div v-if="pendingDisableManualDraw" class="modal-overlay" @click.self="pendingDisableManualDraw = false">
      <section class="message-modal">
        <h2>关闭手写签表</h2>
        <p>已安排的签位会被清空，赛事将改用自动抽签。确定关闭吗？</p>
        <div class="message-modal-actions">
          <button class="ghost-action" type="button" @click="pendingDisableManualDraw = false">取消</button>
          <button class="secondary-action" type="button" @click="confirmDisableManualDraw">确定关闭</button>
        </div>
      </section>
    </div>

    <div v-if="pendingDisableManualGroups" class="modal-overlay" @click.self="pendingDisableManualGroups = false">
      <section class="message-modal">
        <h2>关闭手写分组</h2>
        <p>已安排的小组名单会被清空，赛事将改用自动分组。确定关闭吗？</p>
        <div class="message-modal-actions">
          <button class="ghost-action" type="button" @click="pendingDisableManualGroups = false">取消</button>
          <button class="secondary-action" type="button" @click="confirmDisableManualGroups">确定关闭</button>
        </div>
      </section>
    </div>

    <div v-if="pendingDeleteTeam" class="modal-overlay" @click.self="pendingDeleteTeamId = ''">
      <section class="message-modal">
        <h2>移除队伍</h2>
        <p>确定移除「{{ pendingDeleteTeam.name }}」队吗？</p>
        <div class="message-modal-actions">
          <button class="ghost-action" type="button" @click="pendingDeleteTeamId = ''">取消</button>
          <button class="secondary-action" type="button" @click="confirmDeleteTeam">确定</button>
        </div>
      </section>
    </div>

    <div v-if="pendingDeleteRoundRuleSegment" class="modal-overlay segment-delete-modal" @click.self="pendingDeleteRoundRuleSegmentId = null">
      <section class="message-modal">
        <h2>删除赛段</h2>
        <p>确定删除「{{ pendingDeleteRoundRuleSegment.name || '未命名赛段' }}」吗？</p>
        <div class="message-modal-actions">
          <button class="ghost-action" type="button" @click="pendingDeleteRoundRuleSegmentId = null">取消</button>
          <button class="secondary-action" type="button" @click="confirmRemoveRoundRuleSegment">确定</button>
        </div>
      </section>
    </div>

    <div v-if="roundRuleLimitMessage" class="modal-overlay segment-delete-modal" @click.self="roundRuleLimitMessage = ''">
      <section class="message-modal">
        <h2>赛段已到上限</h2>
        <p>{{ roundRuleLimitMessage }}</p>
        <button class="secondary-action" type="button" @click="roundRuleLimitMessage = ''">知道了</button>
      </section>
    </div>

    <div v-if="roundRuleDrawerOpen" class="drawer-overlay" @click.self="closeRoundRuleDrawer">
      <aside class="round-rule-drawer">
        <div class="drawer-head">
          <div>
            <div class="drawer-title-row">
              <h2>分段规则设计</h2>
              <p>{{ roundRuleDrawerHint }}</p>
            </div>
          </div>
          <button class="ghost-action small" type="button" @click="closeRoundRuleDrawer">关闭</button>
        </div>

        <div class="scope-bank">
          <h3>比赛阶段</h3>
          <div class="scope-chip-list">
            <span
              v-for="scope in roundRuleScopes"
              :key="scope.key"
              class="scope-chip"
              :class="{ assigned: !!assignedSegmentName(scope.key) }"
            >
              {{ scope.label }}
              <em>{{ assignedSegmentName(scope.key) || '未分配' }}</em>
            </span>
          </div>
        </div>

        <p v-if="roundRuleDrawerError" class="drawer-error">{{ roundRuleDrawerError }}</p>

        <div class="segment-list">
          <section v-for="segment in form.roundRuleSegments" :key="segment.id" class="segment-card">
            <div class="segment-card-head">
              <input v-model.trim="segment.name" placeholder="赛段名称" />
              <button
                class="tiny-text-action danger"
                type="button"
                :disabled="form.roundRuleSegments.length <= 1"
                @click="requestRemoveRoundRuleSegment(segment)"
              >
                删除赛段
              </button>
            </div>

            <div class="scope-toggle-grid">
              <button
                v-for="scope in roundRuleScopes"
                :key="scope.key"
                type="button"
                class="scope-toggle"
                :class="{
                  active: segment.scopeKeys.includes(scope.key),
                  unavailable: isScopeAssignedToOtherSegment(segment, scope.key),
                }"
                :disabled="isScopeAssignedToOtherSegment(segment, scope.key)"
                @click="toggleSegmentScope(segment, scope)"
              >
                {{ scope.label }}
              </button>
            </div>

            <div class="field-grid four round-rule-grid">
              <label>
                <span>局数</span>
                <select v-model.number="segment.rule.bestOf" :disabled="isRelay" @change="setBestOf(segment.rule, segment.rule.bestOf)">
                  <option :value="1" v-if="!isVolleyball">一局</option>
                  <option :value="3">三局两胜</option>
                  <option :value="5">五局三胜</option>
                </select>
              </label>
              <label>
                <span>基础胜分</span>
                <input v-model.number="segment.rule.pointsToWin" type="number" min="1" />
              </label>
              <label>
                <span>追分</span>
                <select v-model="segment.rule.enableDeuce">
                  <option :value="true">开启</option>
                  <option :value="false">关闭</option>
                </select>
              </label>
              <label>
                <span>封顶分</span>
                <input v-model.number="segment.rule.capPoint" type="number" min="1" />
              </label>
              <label v-if="isVolleyball">
                <span>决胜局胜分</span>
                <input v-model.number="segment.rule.decidingPointsToWin" type="number" min="1" />
              </label>
            </div>
          </section>
        </div>

        <div class="drawer-actions">
          <button
            class="ghost-action small round-rule-add-action"
            :class="{ 'at-limit': roundRuleSegmentLimitReached }"
            type="button"
            @click="addRoundRuleSegment"
          >
            新增赛段
          </button>
          <div class="drawer-actions-right">
            <button class="ghost-action" type="button" @click="closeRoundRuleDrawer">取消</button>
            <button class="secondary-action" type="button" @click="confirmRoundRuleSegments">确定</button>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { clearToken, createTournament, fetchMe } from '../services/api'
import DrawSlotEditor from '../components/DrawSlotEditor.vue'
import ThemeSwitcher from '../components/ThemeSwitcher.vue'
import GroupAssignmentEditor from '../components/GroupAssignmentEditor.vue'
import {
  DRAW_SLOT_EMPTY,
  MAX_DRAW_SLOTS,
  drawCapacityFor,
  validateDrawSlots,
} from '../utils/drawSlots'

const router = useRouter()
const submitting = ref(false)
const success = ref('')
const modalError = ref('')
const profile = ref(null)
const playerPaste = ref('')
const playerListVisible = ref(false)
const quickTeamName = ref('')
const teamPaste = ref('')
const selectedTeamId = ref('')
const editingTeamName = ref(false)
const teamNameDraft = ref('')
const changingCaptain = ref(false)
const captainCandidateIndex = ref(-1)
const pendingDeleteTeamId = ref('')
const roundRuleDrawerOpen = ref(false)
const roundRuleDrawerError = ref('')
const pendingDeleteRoundRuleSegmentId = ref(null)
const roundRuleLimitMessage = ref('')
const players = reactive([])
const teams = reactive([])
let nextTeamId = 1
let nextRoundRuleSegmentId = 1

const form = reactive({
  name: '',
  location: '',
  sportType: 0,
  participantType: 0,
  teamMatchTemplate: 1,
  tournamentType: 0,
  knockoutRounds: 3,
  knockoutSlots: 8,
  qualifiersPerGroup: 2,
  roundRobinRounds: 1,
  rankingTemplate: 'BWF_BADMINTON',
  rankingPriorities: [],
  thirdPlaceEnabled: false,
  roundRuleEnabled: false,
  roundRules: [],
  roundRuleSegments: [],
  refereePassword: '',
  rule: {
    bestOf: 3,
    gamesToWin: 2,
    pointsToWin: 21,
    decidingPointsToWin: null,
    enableDeuce: true,
    capPoint: 30,
  },
})

const divisionsEnabled = ref(false)
const divisionDrafts = reactive([])
let nextDivisionLocalId = 1

// 多组别模式下的排名规则：仅在“小组赛+淘汰赛”(type1) / “循环赛”(type2) 组别生效，
// 关闭多组别时无需重置（默认值恒定，且只有多组别 payload 会读取）
const divisionRankingTemplate = ref('BWF_BADMINTON')
const divisionRankingTemplateOptions = [
  { value: 'BWF_BADMINTON', name: 'BWF标准规则' },
  { value: 'BADMINTON_COMMON_1', name: '胜场数-净胜局-得失分比' },
  { value: 'BADMINTON_TEAM_COMMON_1', name: '常用模板一' },
]

function createDivisionDraft() {
  return {
    localId: nextDivisionLocalId++,
    name: '',
    playersText: '',
    tournamentType: 0,
    knockoutRounds: 3,
    knockoutSlots: 8,
    qualifiersPerGroup: 2,
    roundRobinRounds: 1,
    rule: {
      bestOf: 3,
      gamesToWin: 2,
      pointsToWin: 21,
      enableDeuce: true,
      capPoint: 30,
    },
    // 小组+淘汰赛制的组别：淘汰阶段规则单独配置（对齐单组别页面的分轮规则能力）
    knockoutRule: {
      bestOf: 3,
      gamesToWin: 2,
      pointsToWin: 21,
      enableDeuce: true,
      capPoint: 30,
    },
    thirdPlaceEnabled: false,
  }
}

function addDivisionDraft() {
  divisionDrafts.push(createDivisionDraft())
}

function removeDivisionDraft(index) {
  divisionDrafts.splice(index, 1)
}

function toggleDivisionsEnabled() {
  divisionsEnabled.value = !divisionsEnabled.value
  if (divisionsEnabled.value && divisionDrafts.length === 0) {
    divisionDrafts.push(createDivisionDraft())
    divisionDrafts.push(createDivisionDraft())
  }
}

function countDivisionPlayers(text) {
  return String(text || '')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .length
}

/** 组别某一阶段规则的合法性（与后端 applyRule 约束一致） */
function checkDivisionRule(label, stageName, rule) {
  if (!rule) return ''
  if (![1, 3, 5].includes(Number(rule.bestOf))) {
    return `${label}${stageName}规则的总局数必须为1、3或5`
  }
  const points = Number(rule.pointsToWin)
  if (!Number.isInteger(points) || points < 1 || points > 99) {
    return `${label}${stageName}规则的每局分必须是1到99之间的整数`
  }
  if (rule.enableDeuce) {
    const cap = Number(rule.capPoint)
    if (!Number.isInteger(cap) || cap <= points || cap > 99) {
      return `${label}${stageName}规则的封顶分需大于每局分且不超过99`
    }
  }
  return ''
}

function onDivisionTypeChange(d) {
  if (Number(d.tournamentType) === 2) d.thirdPlaceEnabled = false
}

function divisionRoundsHint(rounds) {
  const n = Number(rounds)
  if (!Number.isInteger(n) || n < 1 || n > 10) return '-'
  const minExclusive = n === 1 ? 1 : 2 ** (n - 1)
  return `${minExclusive + 1}~${2 ** n}`
}

/** 组别内规则序列化（多组别仅支持羽毛球，不含决胜局分） */
function divisionRulePayload(rule) {
  return {
    bestOf: Number(rule.bestOf),
    gamesToWin: Math.floor(Number(rule.bestOf) / 2) + 1,
    pointsToWin: Number(rule.pointsToWin),
    enableDeuce: rule.enableDeuce,
    capPoint: Number(rule.capPoint),
  }
}

/** 组别级分轮规则：小组赛(0,0) + 淘汰赛各轮(1..N)，N 由该组别淘汰名额推导 */
function buildDivisionRoundRules(d) {
  const rounds = Math.max(1, Math.round(Math.log2(Number(d.knockoutSlots || 2))))
  return [
    { stageType: 0, roundNum: 0, rule: divisionRulePayload(d.rule) },
    ...Array.from({ length: rounds }, (_, index) => ({
      stageType: 1,
      roundNum: index + 1,
      rule: divisionRulePayload(d.knockoutRule),
    })),
  ]
}

function clampDivisionCapPoint(d) {
  const pointsToWin = Number(d.rule.pointsToWin) || 1
  const cap = Number(d.rule.capPoint)
  if (!Number.isFinite(cap)) {
    d.rule.capPoint = Math.min(99, pointsToWin + 1)
    return
  }
  d.rule.capPoint = Math.max(pointsToWin + 1, Math.min(99, Math.round(cap)))
}

const divisionMode = computed(() => divisionsEnabled.value && isIndividual.value && !isVolleyball.value)


const isVolleyball = computed(() => form.sportType === 1)
const isIndividual = computed(() => form.sportType === 0 && form.participantType === 0)
const isBadmintonTeam = computed(() => form.sportType === 0 && form.participantType === 1)
const isRelay = computed(() => isBadmintonTeam.value && form.teamMatchTemplate === 2)
 // 分轮规则仅对"小组赛+淘汰赛"且非接力赛生效（组别模式下由各组别的淘汰赛规则承担，见 divisions payload）
 const supportsRoundRules = computed(() => form.tournamentType !== 2 && !isRelay.value)
const baseRuleOverridden = computed(() => form.roundRuleEnabled && supportsRoundRules.value)
const showPlayerSidePanel = computed(() => isIndividual.value && !divisionMode.value && playerListVisible.value)
const selectedTeam = computed(() => teams.find((team) => team.id === selectedTeamId.value) || null)
const showTeamSidePanel = computed(() => !isIndividual.value && !!selectedTeam.value)
const pendingDeleteTeam = computed(() => teams.find((team) => team.id === pendingDeleteTeamId.value) || null)
const pendingDeleteRoundRuleSegment = computed(() => form.roundRuleSegments.find((segment) => segment.id === pendingDeleteRoundRuleSegmentId.value) || null)
const participantCount = computed(() => (isIndividual.value ? players.filter((player) => player.name).length : teams.length))
const showRankingConfig = computed(() => form.tournamentType === 1)
const knockoutStageSize = computed(() => (form.tournamentType === 1 ? Number(form.knockoutSlots) : participantCount.value))
const canEnableThirdPlace = computed(() => form.tournamentType !== 2 && knockoutStageSize.value >= 4)
// ——— 手写签表（纯淘汰赛手动排签）———
const manualDrawEnabled = ref(false)
const manualSlots = ref([])

/** 名单项：个人赛=已填姓名的选手，团体赛=顶层 teams 顺序；key 即提交顺序的 0-based 下标 */
const manualRoster = computed(() => {
  if (isIndividual.value) {
    return players
      .filter((player) => player.name)
      .map((player, index) => ({ key: String(index), label: player.name }))
  }
  return teams.map((team, index) => ({ key: String(index), label: team.name || '未命名队伍' }))
})
const manualParticipantCount = computed(() => manualRoster.value.length)
const manualCapacity = computed(() => drawCapacityFor(manualParticipantCount.value))
const manualDrawUnavailableReason = computed(() => {
  if (manualCapacity.value > MAX_DRAW_SLOTS) return `手写签表最多支持 ${MAX_DRAW_SLOTS} 个签位`
  if (manualParticipantCount.value < 2) return '至少需要 2 个参赛单位才能手写签表'
  return ''
})
const canEnableManualDraw = computed(() => !manualDrawUnavailableReason.value)
const manualDrawValidation = computed(() => validateDrawSlots(manualSlots.value, manualRoster.value.map((item) => item.key)))
const manualDrawBlocked = computed(() => manualDrawEnabled.value && !manualDrawValidation.value.ok)

function toggleManualDraw() {
  if (manualDrawEnabled.value) {
    // 已有安排时先确认，防止误触清空签位
    if (manualPlacedCount.value > 0) {
      pendingDisableManualDraw.value = true
      return
    }
    manualDrawEnabled.value = false
    return
  }
  if (!canEnableManualDraw.value) {
    modalError.value = manualDrawUnavailableReason.value
    return
  }
  manualDrawEnabled.value = true
  syncManualSlots()
  scrollToManualPanel()
}

const manualDrawPanel = ref(null)
const manualPanelFlash = ref(false)
const manualDrawProblemsOpen = ref(false)
const pendingDisableManualDraw = ref(false)
const editorFlashMatch = ref(-1)
const editorFlashNonce = ref(0)

/** 已显式安排（名单项或轮空）的签位数量 */
const manualPlacedCount = computed(() => manualSlots.value.filter((slot) => slot !== null && slot !== '').length)

const manualDrawProblemLines = computed(() => {
  const result = manualDrawValidation.value
  const lines = []
  if (result.bothByeMatches.length) {
    lines.push(`第 ${result.bothByeMatches.map((index) => index + 1).join('、')} 场的两个签位都是轮空`)
  }
  if (result.unplacedKeys.length) lines.push(`还有 ${result.unplacedKeys.length} 个名单项没有安排签位`)
  if (result.hasEmpty) lines.push('还有签位停留在「未选择」')
  if (result.duplicatedKeys.length) lines.push('同一个名单项出现在多个签位上')
  return lines.length ? lines : [result.message || '签表尚未完成']
})

function confirmDisableManualDraw() {
  pendingDisableManualDraw.value = false
  manualDrawEnabled.value = false
}

/** 开启/跳转时把手写签表面板滚入视口并高亮，避免“点了开关没反应” */
async function scrollToManualPanel() {
  await nextTick()
  const el = manualDrawPanel.value
  if (!el) return
  el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  manualPanelFlash.value = false
  requestAnimationFrame(() => {
    manualPanelFlash.value = true
    setTimeout(() => {
      manualPanelFlash.value = false
    }, 1800)
  })
}

function firstProblemMatchIndex() {
  const result = manualDrawValidation.value
  if (result.bothByeMatches.length) return result.bothByeMatches[0]
  const slots = manualSlots.value
  for (let index = 0; index < slots.length; index += 2) {
    if (slots[index] === '' || slots[index + 1] === '') return index / 2
  }
  return -1
}

/** 提交被手写签表校验拦下时，弹出问题清单并可一键跳到问题场次 */
function onSubmitClick() {
  if (manualDrawBlocked.value) {
    manualDrawProblemsOpen.value = true
    return
  }
  if (manualGroupsBlocked.value) {
    manualGroupsProblemsOpen.value = true
    return
  }
  submit()
}

function jumpToDrawProblem() {
  manualDrawProblemsOpen.value = false
  editorFlashMatch.value = firstProblemMatchIndex()
  editorFlashNonce.value += 1
  scrollToManualPanel()
}

/** 名单增删时把签位数组对齐到框架容量，并丢弃已不存在的名单项 */
function syncManualSlots() {
  const keys = new Set(manualRoster.value.map((item) => item.key))
  const previous = manualSlots.value
  manualSlots.value = Array.from({ length: manualCapacity.value }, (_, index) => {
    const current = previous[index]
    if (current === null) return null
    return keys.has(current) ? current : DRAW_SLOT_EMPTY
  })
}

// ——— 手写分组（小组+淘汰赛手动分组）———
const manualGroupsEnabled = ref(false)
/** 二维数组：元素 = 该组名单项 key（= 创建页名单提交顺序的 0-based 下标字符串），组内顺序即组内座次 */
const manualGroups = ref([])
const manualGroupsPanel = ref(null)
const manualGroupsPanelFlash = ref(false)
const manualGroupsProblemsOpen = ref(false)
const pendingDisableManualGroups = ref(false)

/** 契约：组数 = 淘汰名额 ÷ 每组出线（必须整除，且至少 2 个淘汰名额） */
const manualGroupCount = computed(() => {
  const slots = Number(form.knockoutSlots)
  const perGroup = Number(form.qualifiersPerGroup)
  if (!Number.isInteger(slots) || !Number.isInteger(perGroup) || perGroup < 1 || slots < 2) return 0
  if (slots % perGroup !== 0) return 0
  return slots / perGroup
})
const groupCountValid = computed(() => manualGroupCount.value >= 1)
/** 契约：每组人数下限 = max(2, 每组出线) */
const manualMinPerGroup = computed(() => Math.max(2, Number(form.qualifiersPerGroup) || 0))
const manualGroupsUnavailableReason = computed(() => {
  if (!groupCountValid.value) return '淘汰名额需能被每组出线整除才能手写分组'
  const required = manualGroupCount.value * manualMinPerGroup.value
  if (manualParticipantCount.value < required) {
    return `手写分组至少需要 ${required} 个参赛单位（${manualGroupCount.value} 组 × 每组 ${manualMinPerGroup.value} 人）`
  }
  return ''
})
const canEnableManualGroups = computed(() => !manualGroupsUnavailableReason.value)
/** 已分组人数 */
const manualGroupsPlacedCount = computed(() => manualGroups.value
  .reduce((total, group) => total + (Array.isArray(group) ? group.length : 0), 0))

/** 客户端校验：组数、每组下限、每人恰属一组（全覆盖、无重复、无越界下标） */
const manualGroupsValidation = computed(() => {
  const problems = []
  if (!groupCountValid.value) {
    problems.push('淘汰名额需能被每组出线整除（至少 2 个淘汰名额）')
    return { ok: false, message: problems.join('；'), problems }
  }
  const list = manualGroups.value.map((group) => (Array.isArray(group) ? group.map((key) => String(key)) : []))
  const rosterKeys = manualRoster.value.map((item) => String(item.key))
  const known = new Set(rosterKeys)
  const seen = new Set()
  const duplicated = new Set()
  for (const group of list) {
    for (const key of group) {
      if (seen.has(key)) duplicated.add(key)
      seen.add(key)
    }
  }
  const unknownCount = [...seen].filter((key) => !known.has(key)).length
  const unplacedCount = rosterKeys.filter((key) => !seen.has(key)).length
  const shortGroups = list
    .map((group, index) => ({ index, size: group.length }))
    .filter((item) => item.size < manualMinPerGroup.value)
  if (list.length !== manualGroupCount.value) problems.push(`小组数量应为 ${manualGroupCount.value} 个`)
  if (unknownCount) problems.push(`有 ${unknownCount} 个名单项已不在名单中，请先移出`)
  if (duplicated.size) problems.push(`有 ${duplicated.size} 个名单项出现在多个小组`)
  if (unplacedCount) problems.push(`还有 ${unplacedCount} 个名单项没有分组`)
  for (const item of shortGroups) problems.push(`第 ${item.index + 1} 组不足 ${manualMinPerGroup.value} 人`)
  return { ok: !problems.length, message: problems.join('；'), problems }
})
const manualGroupsBlocked = computed(() => manualGroupsEnabled.value && !manualGroupsValidation.value.ok)
const manualGroupProblemLines = computed(() => {
  const result = manualGroupsValidation.value
  return result.problems.length ? result.problems : [result.message || '分组尚未完成']
})

function toggleManualGroups() {
  if (manualGroupsEnabled.value) {
    // 已有安排时先确认，防止误触清空分组
    if (manualGroupsPlacedCount.value > 0) {
      pendingDisableManualGroups.value = true
      return
    }
    manualGroupsEnabled.value = false
    return
  }
  if (!canEnableManualGroups.value) {
    modalError.value = manualGroupsUnavailableReason.value
    return
  }
  manualGroupsEnabled.value = true
  syncManualGroups()
  scrollToManualGroupsPanel()
}

function confirmDisableManualGroups() {
  pendingDisableManualGroups.value = false
  manualGroupsEnabled.value = false
}

/** 开启/跳转时把手写分组面板滚入视口并高亮，避免“点了开关没反应” */
async function scrollToManualGroupsPanel() {
  await nextTick()
  const el = manualGroupsPanel.value
  if (!el) return
  el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  manualGroupsPanelFlash.value = false
  requestAnimationFrame(() => {
    manualGroupsPanelFlash.value = true
    setTimeout(() => {
      manualGroupsPanelFlash.value = false
    }, 1800)
  })
}

/**
 * 名单/组数变化时对齐 groups：组数不足补空组；已不存在的名单项与重复项清理掉。
 * 组数减少时被裁掉的小组成员退回「未分组」（由用户在下方面板重新安排），避免座次被静默迁移。
 */
function syncManualGroups() {
  const count = manualGroupCount.value
  if (!count) {
    manualGroups.value = []
    return
  }
  const keys = new Set(manualRoster.value.map((item) => String(item.key)))
  const seen = new Set()
  manualGroups.value = Array.from({ length: count }, (_, index) => {
    const group = Array.isArray(manualGroups.value[index]) ? manualGroups.value[index] : []
    const cleaned = []
    for (const raw of group) {
      const key = String(raw)
      if (!keys.has(key) || seen.has(key)) continue
      seen.add(key)
      cleaned.push(key)
    }
    return cleaned
  })
}

/** 提交被分组校验拦下时，跳到分组面板处理 */
function jumpToGroupProblem() {
  manualGroupsProblemsOpen.value = false
  scrollToManualGroupsPanel()
}
const roundRuleScopes = computed(() => expectedRoundRuleScopes())
const activeRoundRuleSegments = computed(() => form.roundRuleSegments.filter((segment) => segment.scopeKeys.length))
const roundRuleSegmentLimit = computed(() => roundRuleScopes.value.length)
const roundRuleSegmentLimitReached = computed(() => roundRuleSegmentLimit.value > 0 && form.roundRuleSegments.length >= roundRuleSegmentLimit.value)
const roundRuleDrawerHint = computed(() => {
  if (form.tournamentType === 1) return `小组赛 + ${Math.log2(form.knockoutSlots)} 轮淘汰赛`
  return `${form.knockoutRounds} 轮淘汰赛`
})
const rankingTemplateOptions = computed(() => {
  if (isRelay.value) {
    return [
      { value: 'BADMINTON_RELAY_COMMON_1', name: '接力追分常用', desc: '胜场数 → 两队直胜 → 小分得失比' },
    ]
  }
  if (isBadmintonTeam.value) {
    return [
      { value: 'BADMINTON_TEAM_COMMON_1', name: '羽毛球团体常用', desc: '胜场数 → 胜负关系 → 场内大分 → 场内局 → 场内小分' },
    ]
  }
  if (isVolleyball.value) {
    return [
      { value: 'FIVB_VOLLEYBALL', name: 'FIVB 标准规则', desc: '胜场数 → 积分 → 胜负局比 → 得失分比 → 胜负关系' },
      { value: 'CAMPUS_VOLLEYBALL', name: '校园排球常用', desc: '胜场数 → 净胜局 → 净胜分 → 胜负关系' },
      { value: 'VOLLEYBALL_COMMON_1', name: '排球常用规则', desc: '胜场数 → 胜局 → 得失分比' },
    ]
  }
  return [
    { value: 'BWF_BADMINTON', name: 'BWF 标准规则', desc: '胜场数 → 净胜局 → 净胜分 → 胜负关系' },
    { value: 'BADMINTON_COMMON_1', name: '羽毛球常用规则', desc: '胜场数 → 净胜局 → 得失分比' },
  ]
})
const selectedRankingTemplateDesc = computed(() => (
  rankingTemplateOptions.value.find((option) => option.value === form.rankingTemplate)?.desc || ''
))

function createRule(source = form.rule) {
  return {
    bestOf: source.bestOf,
    gamesToWin: source.gamesToWin,
    pointsToWin: source.pointsToWin,
    decidingPointsToWin: isVolleyball.value ? (source.decidingPointsToWin || 15) : null,
    enableDeuce: source.enableDeuce,
    capPoint: source.capPoint,
  }
}

function createDefaultRuleForCurrentSport() {
  if (isVolleyball.value) {
    return {
      bestOf: 3,
      gamesToWin: 2,
      pointsToWin: 25,
      decidingPointsToWin: 15,
      enableDeuce: true,
      capPoint: 99,
    }
  }
  return {
    bestOf: 3,
    gamesToWin: 2,
    pointsToWin: 21,
    decidingPointsToWin: null,
    enableDeuce: true,
    capPoint: 30,
  }
}

function knockoutCapacity() {
  if (form.tournamentType === 1) return form.knockoutSlots
  const rounds = Number(form.knockoutRounds)
  if (!Number.isInteger(rounds) || rounds < 1 || rounds > 10) return 0
  return 2 ** rounds
}

function adjustKnockoutRounds(delta) {
  const current = Number(form.knockoutRounds) || 1
  form.knockoutRounds = Math.min(10, Math.max(1, current + delta))
}

const knockoutParticipantRangeText = computed(() => {
  const rounds = Number(form.knockoutRounds)
  if (!Number.isInteger(rounds) || rounds < 1 || rounds > 10) return ''
  const minCount = rounds === 1 ? 2 : 2 ** (rounds - 1) + 1
  const maxCount = 2 ** rounds
  return `(共${minCount}-${maxCount}${isIndividual.value ? '人' : '队'})`
})

function validateKnockoutRounds(count) {
  if (form.tournamentType !== 0) return ''
  const rounds = Number(form.knockoutRounds)
  if (!Number.isInteger(rounds) || rounds < 1 || rounds > 10) return '淘汰轮数必须是1到10之间的整数'
  const minExclusive = rounds === 1 ? 1 : 2 ** (rounds - 1)
  const maxInclusive = 2 ** rounds
  if (count <= minExclusive || count > maxInclusive) {
    return `当前淘汰轮数需要${minExclusive + 1}到${maxInclusive}名参赛方`
  }
  return ''
}

function expectedRoundRuleScopes() {
  if (!supportsRoundRules.value) return []
  const scopes = []
  if (form.tournamentType === 1) {
    scopes.push({ stageType: 0, roundNum: 0, key: roundRuleScopeKey(0, 0), label: '小组赛' })
  }
  const capacity = knockoutCapacity()
  if (capacity < 2) return scopes
  const roundCount = Math.log2(capacity)
  for (let round = 1; round <= roundCount; round++) {
    const from = capacity / (2 ** (round - 1))
    const to = from / 2
    scopes.push({
      stageType: 1,
      roundNum: round,
      key: roundRuleScopeKey(1, round),
      label: to === 1 ? '决赛' : `${from}进${to}`,
    })
  }
  return scopes
}

function roundRuleScopeKey(stageType, roundNum) {
  return `${stageType}-${roundNum}`
}

function syncRoundRules(options = {}) {
  if (!supportsRoundRules.value) {
    form.roundRuleEnabled = false
    form.roundRules = []
    form.roundRuleSegments = []
    return
  }
  if (!form.roundRuleEnabled) {
    form.roundRules = []
    form.roundRuleSegments = []
    return
  }
  normalizeRoundRuleSegments(options)
  updateFlattenedRoundRules()
}

function normalizeRoundRuleSegments(options = {}) {
  const scopes = expectedRoundRuleScopes()
  const scopeOrder = new Map(scopes.map((scope, index) => [scope.key, index]))
  const scopeKeys = new Set(scopes.map((scope) => scope.key))
  const previousRules = new Map(form.roundRules.map((item) => [roundRuleScopeKey(item.stageType, item.roundNum), item.rule]))

  if (!form.roundRuleSegments.length && !previousRules.size && scopes.length) {
    form.roundRuleSegments = [{
      id: nextRoundRuleSegmentId++,
      name: '赛段1',
      scopeKeys: [scopes[0].key],
      rule: createRule(),
    }]
    return
  }

  const segments = form.roundRuleSegments
    .map((segment) => ({
      ...segment,
      scopeKeys: segment.scopeKeys.filter((key) => scopeKeys.has(key)),
      rule: options.resetRules ? createRule() : segment.rule,
    }))

  if (!segments.length && scopes.length) {
    segments.push({
      id: nextRoundRuleSegmentId++,
      name: '赛段1',
      scopeKeys: [scopes[0].key],
      rule: options.resetRules ? createRule() : (previousRules.get(scopes[0].key) || createRule()),
    })
  }

  for (const segment of segments) {
    segment.scopeKeys.sort((left, right) => scopeOrder.get(left) - scopeOrder.get(right))
  }
  form.roundRuleSegments = segments
}

function updateFlattenedRoundRules() {
  const scopeMap = new Map(expectedRoundRuleScopes().map((scope) => [scope.key, scope]))
  form.roundRules = form.roundRuleSegments.flatMap((segment) => segment.scopeKeys
    .filter((key) => scopeMap.has(key))
    .map((key) => {
      const scope = scopeMap.get(key)
      return {
        stageType: scope.stageType,
        roundNum: scope.roundNum,
        label: scope.label,
        rule: segment.rule,
      }
    }))
}

function openRoundRuleDrawer() {
  syncRoundRules()
  roundRuleDrawerError.value = ''
  roundRuleDrawerOpen.value = true
}

function closeRoundRuleDrawer() {
  pendingDeleteRoundRuleSegmentId.value = null
  roundRuleLimitMessage.value = ''
  roundRuleDrawerOpen.value = false
}

function addRoundRuleSegment() {
  roundRuleDrawerError.value = ''
  if (roundRuleSegmentLimitReached.value) {
    roundRuleLimitMessage.value = `当前赛制最多划分 ${roundRuleSegmentLimit.value} 个赛段。`
    return
  }
  const finalScope = roundRuleScopes.value[roundRuleScopes.value.length - 1]
  const finalScopeKeys = form.roundRuleSegments.length > 0 && finalScope ? [finalScope.key] : []
  if (finalScopeKeys.length) {
    for (const segment of form.roundRuleSegments) {
      segment.scopeKeys = segment.scopeKeys.filter((key) => !finalScopeKeys.includes(key))
    }
  }
  form.roundRuleSegments.push({
    id: nextRoundRuleSegmentId++,
    name: `赛段${form.roundRuleSegments.length + 1}`,
    scopeKeys: finalScopeKeys,
    rule: createRule(),
  })
  updateFlattenedRoundRules()
}

function removeRoundRuleSegment(segment) {
  if (form.roundRuleSegments.length <= 1) return
  roundRuleDrawerError.value = ''
  form.roundRuleSegments = form.roundRuleSegments.filter((item) => item.id !== segment.id)
  updateFlattenedRoundRules()
}

function requestRemoveRoundRuleSegment(segment) {
  if (form.roundRuleSegments.length <= 1) return
  pendingDeleteRoundRuleSegmentId.value = segment.id
}

function confirmRemoveRoundRuleSegment() {
  const segment = pendingDeleteRoundRuleSegment.value
  pendingDeleteRoundRuleSegmentId.value = null
  if (segment) removeRoundRuleSegment(segment)
}

function toggleSegmentScope(segment, scope) {
  roundRuleDrawerError.value = ''
  if (segment.scopeKeys.includes(scope.key)) {
    segment.scopeKeys = segment.scopeKeys.filter((key) => key !== scope.key)
  } else {
    for (const item of form.roundRuleSegments) {
      item.scopeKeys = item.scopeKeys.filter((key) => key !== scope.key)
    }
    segment.scopeKeys.push(scope.key)
    const scopeOrder = new Map(roundRuleScopes.value.map((item, index) => [item.key, index]))
    segment.scopeKeys.sort((left, right) => scopeOrder.get(left) - scopeOrder.get(right))
  }
  updateFlattenedRoundRules()
}

function assignedSegmentName(scopeKey) {
  const segment = form.roundRuleSegments.find((item) => item.scopeKeys.includes(scopeKey))
  return segment?.name || ''
}

function isScopeAssignedToOtherSegment(segment, scopeKey) {
  return form.roundRuleSegments.some((item) => item !== segment && item.scopeKeys.includes(scopeKey))
}

function formatSegmentScopes(segment) {
  const scopeMap = new Map(roundRuleScopes.value.map((scope) => [scope.key, scope.label]))
  return segment.scopeKeys.map((key) => scopeMap.get(key)).filter(Boolean).join('、') || '未选择'
}

function validateRoundRuleSegments() {
  const scopes = roundRuleScopes.value
  if (!scopes.length) return '请先设置有效的淘汰轮数'
  const assigned = new Set()
  for (const segment of form.roundRuleSegments) {
    if (!segment.scopeKeys.length) return `请为「${segment.name || '未命名赛段'}」选择比赛阶段，或删除该赛段`
    for (const key of segment.scopeKeys) {
      if (assigned.has(key)) return '同一个比赛阶段不能重复分配'
      assigned.add(key)
    }
  }
  const missing = scopes.filter((scope) => !assigned.has(scope.key))
  if (missing.length) return `还有比赛阶段未分配：${missing.map((scope) => scope.label).join('、')}`
  return ''
}

function confirmRoundRuleSegments() {
  const error = validateRoundRuleSegments()
  if (error) {
    roundRuleDrawerError.value = error
    return
  }
  updateFlattenedRoundRules()
  roundRuleDrawerOpen.value = false
}

function serializeRule(rule) {
  return {
    bestOf: rule.bestOf,
    gamesToWin: rule.gamesToWin,
    pointsToWin: rule.pointsToWin,
    decidingPointsToWin: isVolleyball.value ? rule.decidingPointsToWin : undefined,
    enableDeuce: rule.enableDeuce,
    capPoint: rule.capPoint,
  }
}

function ruleForThirdPlace() {
  if (!form.roundRuleEnabled || !supportsRoundRules.value) return form.rule
  const finalRound = Math.log2(knockoutCapacity())
  const finalRule = form.roundRules.find((item) => item.stageType === 1 && item.roundNum === finalRound)?.rule
  return finalRule || form.rule
}

function defaultRankingTemplateForCurrentMode() {
  return rankingTemplateOptions.value[0]?.value || 'BWF_BADMINTON'
}

function syncRankingTemplate() {
  if (!rankingTemplateOptions.value.some((option) => option.value === form.rankingTemplate)) {
    form.rankingTemplate = defaultRankingTemplateForCurrentMode()
    form.rankingPriorities = []
  }
}

function syncThirdPlaceAvailability() {
  if (!canEnableThirdPlace.value) {
    form.thirdPlaceEnabled = false
  }
}

function setBestOf(rule, bestOf) {
  rule.bestOf = Number(bestOf)
  rule.gamesToWin = Math.floor(rule.bestOf / 2) + 1
}

function syncSportDefaults() {
  // 关闭多组别时无需重置 divisionRankingTemplate：仅多组别 payload 使用，默认值恒定
  if (form.sportType !== 0 || form.participantType !== 0) divisionsEnabled.value = false
  if (form.sportType === 1) {
    form.participantType = 1
    form.teamMatchTemplate = 0
    Object.assign(form.rule, createDefaultRuleForCurrentSport())
    fillMissingJerseyNumbers()
  } else {
    form.participantType = 0
    form.teamMatchTemplate = 1
    Object.assign(form.rule, createDefaultRuleForCurrentSport())
  }
  syncRankingTemplate()
  syncThirdPlaceAvailability()
  syncRoundRules({ resetRules: true })
}

function syncParticipantDefaults() {
  // 同上：divisionRankingTemplate 与单组别/团队赛模式无关，无需重置
  if (!(form.sportType === 0 && form.participantType === 0)) divisionsEnabled.value = false
  if (form.participantType === 0) {
    form.teamMatchTemplate = 0
  } else {
    form.teamMatchTemplate = 1
  }
  syncTemplateDefaults()
  syncRankingTemplate()
  syncRoundRules()
}

function syncTemplateDefaults() {
  if (isRelay.value) {
    form.rule.bestOf = 1
    form.rule.gamesToWin = 1
    form.rule.pointsToWin = 10
    form.rule.enableDeuce = false
    form.rule.capPoint = 6
  } else {
    setBestOf(form.rule, 3)
    form.rule.enableDeuce = true
    form.rule.capPoint = form.sportType === 1 ? 99 : 30
  }
  syncRankingTemplate()
  syncThirdPlaceAvailability()
  syncRoundRules()
}

function applyPlayersPaste() {
  const parsed = playerPaste.value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const match = line.match(/^(\d+)[.\s、-]*(.+)$/)
      return match
        ? { seed: Number(match[1]), name: match[2].trim() }
        : { seed: null, name: line }
    })
  players.splice(0, players.length, ...parsed)
  playerListVisible.value = parsed.length > 0
  syncRoundRules()
}

function createMember(name = '', jerseyNumber = '', captain = false) {
  return { name, jerseyNumber, captain }
}

function createTeam(name = '') {
  return { id: `team-${nextTeamId++}`, name, seed: null, members: [createMember('', '', true)] }
}

function quickAddTeam() {
  const members = teamPaste.value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line, index) => parseTeamMemberLine(line, index))
  if (!quickTeamName.value || !members.length) {
    modalError.value = !quickTeamName.value ? '请先填写队名' : '请粘贴队员名单'
    return
  }
  const team = { id: `team-${nextTeamId++}`, name: quickTeamName.value, seed: null, members }
  teams.push(team)
  selectedTeamId.value = team.id
  quickTeamName.value = ''
  teamPaste.value = ''
  syncRoundRules()
}

function parseTeamMemberLine(line, index) {
  if (isVolleyball.value) {
    const match = line.match(/^(.+?)\s+(\d+)$/)
    return createMember(
      match ? match[1].trim() : line,
      match ? Number(match[2]) : index + 1,
      index === 0,
    )
  }
  const name = line.replace(/^\d+[.\s、]+/, '').trim()
  return createMember(name, '', index === 0)
}

function selectTeam(teamId) {
  selectedTeamId.value = teamId
  editingTeamName.value = false
  changingCaptain.value = false
  captainCandidateIndex.value = -1
}

function startTeamNameEdit() {
  teamNameDraft.value = selectedTeam.value?.name || ''
  editingTeamName.value = true
}

function confirmTeamNameEdit() {
  if (selectedTeam.value && teamNameDraft.value) {
    selectedTeam.value.name = teamNameDraft.value
  }
  editingTeamName.value = false
}

function captainIndexOf(team) {
  const savedCaptainIndex = team.members.findIndex((member) => member.name && member.captain)
  return savedCaptainIndex >= 0
    ? savedCaptainIndex
    : team.members.findIndex((member) => member.name)
}

function isCaptainMember(team, index) {
  return captainIndexOf(team) === index
}

function startCaptainChange() {
  captainCandidateIndex.value = captainIndexOf(selectedTeam.value)
  changingCaptain.value = true
}

function confirmCaptainChange() {
  if (!selectedTeam.value || captainCandidateIndex.value < 0) return
  const candidate = selectedTeam.value.members[captainCandidateIndex.value]
  if (!candidate?.name) return
  selectedTeam.value.members.forEach((member, index) => {
    member.captain = index === captainCandidateIndex.value
  })
  changingCaptain.value = false
}

function ensureTeamCaptain(team) {
  const captainIndex = captainIndexOf(team)
  team.members.forEach((member, index) => {
    member.captain = index === captainIndex
  })
}

function deleteMember(team, memberIndex) {
  team.members.splice(memberIndex, 1)
  ensureTeamCaptain(team)
  if (changingCaptain.value) {
    captainCandidateIndex.value = captainIndexOf(team)
  }
}

function nextJerseyNumber(team) {
  const jerseys = team.members
    .map((member) => Number(member.jerseyNumber))
    .filter((number) => Number.isFinite(number) && number > 0)
  return jerseys.length ? Math.max(...jerseys) + 1 : 1
}

function fillMissingJerseyNumbers() {
  teams.forEach((team) => {
    team.members.forEach((member) => {
      const jerseyNumber = Number(member.jerseyNumber)
      if (!Number.isFinite(jerseyNumber) || jerseyNumber <= 0) {
        member.jerseyNumber = nextJerseyNumber(team)
      }
    })
  })
}

function addTeamMember(team) {
  team.members.push(createMember('', isVolleyball.value ? nextJerseyNumber(team) : ''))
}

function requestDeleteTeam(teamId) {
  pendingDeleteTeamId.value = teamId
}

function confirmDeleteTeam() {
  const teamId = pendingDeleteTeamId.value
  pendingDeleteTeamId.value = ''
  deleteTeam(teamId)
}

function deleteTeam(teamId) {
  const teamIndex = teams.findIndex((team) => team.id === teamId)
  if (teamIndex < 0) return
  teams.splice(teamIndex, 1)
  if (selectedTeamId.value !== teamId) return
  selectedTeamId.value = teams[Math.min(teamIndex, teams.length - 1)]?.id || ''
  editingTeamName.value = false
  changingCaptain.value = false
  captainCandidateIndex.value = -1
  syncRoundRules()
}

function logout() {
  clearToken()
  router.replace('/login')
}

async function loadProfile() {
  try {
    profile.value = await fetchMe()
  } catch {
    profile.value = null
  }
}

function validate() {
  if (profile.value && !profile.value.profileCompleted) return '请先在微信小程序「我的」页完善资料（昵称与头像），再创建赛事'
  if (!form.name) return '请填写赛事名称'
  if (form.refereePassword && !/^\d{8,12}$/.test(form.refereePassword)) return '裁判密码需为8~12位数字'
  if (divisionMode.value) {
    if (divisionDrafts.length < 2) return '多组别模式至少需要2个组别'
    if (divisionDrafts.length > 16) return '组别数量不能超过16个'
    const seenDivisionNames = new Set()
    for (const [dIndex, d] of divisionDrafts.entries()) {
      const divisionLabel = `组别 ${dIndex + 1}`
      if (!d.name.trim()) return `${divisionLabel}：请填写组名`
      if (seenDivisionNames.has(d.name.trim())) return `组名「${d.name.trim()}」重复，请修改后再创建`
      seenDivisionNames.add(d.name.trim())
      const divisionPlayerCount = countDivisionPlayers(d.playersText)
      if (divisionPlayerCount < 2) return `${divisionLabel}「${d.name.trim()}」至少需要2名选手`
      if (d.tournamentType === 1 && Number(d.knockoutSlots) > divisionPlayerCount) return `${divisionLabel}「${d.name.trim()}」淘汰名额不能多于选手人数`
      if (d.thirdPlaceEnabled) {
        const thirdPlaceCount = d.tournamentType === 1 ? Number(d.knockoutSlots) : divisionPlayerCount
        if (thirdPlaceCount < 4) return `${divisionLabel}「${d.name.trim()}」开启季军赛需要至少4个淘汰阶段参赛单位`
      }
      if (d.tournamentType === 0) {
        const rounds = Number(d.knockoutRounds)
        if (!Number.isInteger(rounds) || rounds < 1 || rounds > 10) return `${divisionLabel}「${d.name.trim()}」淘汰轮数必须是1到10之间的整数`
        const minExclusive = rounds === 1 ? 1 : 2 ** (rounds - 1)
        const maxInclusive = 2 ** rounds
        if (divisionPlayerCount <= minExclusive || divisionPlayerCount > maxInclusive) {
          return `${divisionLabel}「${d.name.trim()}」当前淘汰轮数需要${minExclusive + 1}到${maxInclusive}名选手`
        }
      }
      const ruleError = checkDivisionRule(`${divisionLabel}「${d.name.trim()}」`, '', d.rule)
      if (ruleError) return ruleError
      if (d.tournamentType === 1) {
        const knockoutRuleError = checkDivisionRule(`${divisionLabel}「${d.name.trim()}」`, '淘汰赛', d.knockoutRule)
        if (knockoutRuleError) return knockoutRuleError
      }
    }
    return ''
  }
  if (manualDrawEnabled.value) {
    if (manualSlots.value.length !== manualCapacity.value) return '签位数量与参赛单位数不匹配，请核对名单后重新填写'
    const manualDrawError = manualDrawValidation.value.message
    if (manualDrawError) return manualDrawError
  }
  if (manualGroupsEnabled.value) {
    const manualGroupsError = manualGroupsValidation.value.message
    if (manualGroupsError) return manualGroupsError
  }
  if (isIndividual.value) {
    const validPlayers = players.filter((player) => player.name)
    if (validPlayers.length < 2) return '个人赛至少需要2名选手'
    const knockoutRoundsError = validateKnockoutRounds(validPlayers.length)
    if (knockoutRoundsError) return knockoutRoundsError
    if (form.roundRuleEnabled && !supportsRoundRules.value) return '当前赛制不支持分轮规则'
    if (form.roundRuleEnabled) {
      const roundRuleSegmentError = validateRoundRuleSegments()
      if (roundRuleSegmentError) return roundRuleSegmentError
    }
    if (form.roundRuleEnabled && !form.roundRules.length) return '请先生成选手名单后再启用分轮规则'
    return ''
  }

  if (teams.length < 2) return '至少需要2支队伍'
  const seenSeeds = new Set()
  for (const team of teams) {
    if (team.seed === null || team.seed === undefined || team.seed === '') continue
    const seed = Number(team.seed)
    if (!Number.isInteger(seed) || seed < 1) return `${team.name || '未命名队伍'} 的种子序号必须是正整数`
    if (seenSeeds.has(seed)) return `种子序号 ${seed} 重复，请修改后再创建比赛`
    seenSeeds.add(seed)
  }
  const knockoutRoundsError = validateKnockoutRounds(teams.length)
  if (knockoutRoundsError) return knockoutRoundsError
  if (form.roundRuleEnabled && !supportsRoundRules.value) return '当前赛制不支持分轮规则'
  if (form.roundRuleEnabled) {
    const roundRuleSegmentError = validateRoundRuleSegments()
    if (roundRuleSegmentError) return roundRuleSegmentError
  }
  if (form.roundRuleEnabled && !form.roundRules.length) return '请先生成参赛名单后再启用分轮规则'
  for (const team of teams) {
    if (!team.name) return '请填写所有队伍名称'
    const validMembers = team.members.filter((member) => member.name)
    if (isVolleyball.value && validMembers.length < 6) return `${team.name} 至少需要6名球员`
    if (!isVolleyball.value && validMembers.length < 2) return `${team.name} 至少需要2名成员`
    if (isVolleyball.value) {
      const jerseys = new Set()
      for (const member of validMembers) {
        if (!Number.isFinite(Number(member.jerseyNumber)) || Number(member.jerseyNumber) <= 0) return `${team.name} 存在无效号码`
        if (jerseys.has(Number(member.jerseyNumber))) return `${team.name} 号码不能重复`
        jerseys.add(Number(member.jerseyNumber))
      }
    }
    if (isRelay.value && validMembers.length < form.rule.capPoint) return `${team.name} 人数不能少于轮转人数`
  }
  return ''
}

function buildPayload() {
  if (divisionMode.value) {
    return {
      name: form.name.trim(),
      location: form.location.trim() || undefined,
      sportType: 0,
      participantType: 0,
      teamMatchTemplate: 0,
      refereePassword: form.refereePassword.trim() || undefined,
      divisions: divisionDrafts.map((d) => ({
        name: d.name.trim(),
        tournamentType: d.tournamentType,
        knockoutRounds: d.tournamentType === 0 ? Number(d.knockoutRounds) : undefined,
        knockoutSlots: d.tournamentType === 1 ? Number(d.knockoutSlots) : undefined,
        qualifiersPerGroup: d.tournamentType === 1 ? Number(d.qualifiersPerGroup) : undefined,
        roundRobinRounds: d.tournamentType === 2 ? Number(d.roundRobinRounds) : undefined,
        // 仅“小组赛+淘汰赛”(1) / “循环赛”(2) 组别需要排名配置，纯淘汰组别不传
        rankingTemplate: d.tournamentType === 1 || d.tournamentType === 2
          ? divisionRankingTemplate.value
          : undefined,
        thirdPlaceEnabled: d.thirdPlaceEnabled,
        rule: divisionRulePayload(d.rule),
        // 小组+淘汰的组别支持"淘汰阶段单独规则"（对齐单组别页面的分轮规则能力）
        roundRuleEnabled: d.tournamentType === 1,
        roundRules: d.tournamentType === 1 ? buildDivisionRoundRules(d) : undefined,
        players: d.playersText
          .split(/\r?\n/)
          .map((line) => line.trim())
          .filter(Boolean)
          .map((line) => {
            const m = line.match(/^(\d+)[.\s、-]*(.+)$/)
            return m ? { seed: Number(m[1]), name: m[2].trim() } : { name: line }
          }),
      })),
    }
  }
  const thirdPlaceRule = ruleForThirdPlace()
  const base = {
    name: form.name,
    location: form.location || undefined,
    sportType: form.sportType,
    tournamentType: form.tournamentType,
    knockoutRounds: form.tournamentType === 0 ? form.knockoutRounds : undefined,
    knockoutSlots: form.tournamentType === 1 ? form.knockoutSlots : undefined,
    qualifiersPerGroup: form.tournamentType === 1 ? form.qualifiersPerGroup : undefined,
    roundRobinRounds: form.tournamentType === 2 ? form.roundRobinRounds : undefined,
    refereePassword: form.refereePassword || undefined,
    // 手写签表：drawMode=manual 时提交签位顺序（元素=名单提交顺序下标，null=轮空），缺省即后端原有的 auto 行为
    drawMode: manualGroupsEnabled.value && form.tournamentType === 1 && !divisionMode.value
      ? 'manual-groups'
      : (manualDrawEnabled.value ? 'manual' : undefined),
    knockoutSlotOrder: manualDrawEnabled.value
      ? manualSlots.value.map((slot) => (slot === null || slot === DRAW_SLOT_EMPTY ? null : Number(slot)))
      : undefined,
    // 手写分组：drawMode=manual-groups 时提交二维分组（元素=名单提交顺序下标，组内顺序即组内座次），缺省即后端自动分组
    groups: manualGroupsEnabled.value && form.tournamentType === 1 && !divisionMode.value
      ? manualGroups.value.map((group) => group.map((key) => Number(key)))
      : undefined,
    rankingTemplate: form.tournamentType === 1 ? form.rankingTemplate : undefined,
    rankingPriorities: form.tournamentType === 1 && form.rankingTemplate === 'CUSTOM'
      ? form.rankingPriorities
      : undefined,
    thirdPlaceEnabled: form.tournamentType !== 2 && form.thirdPlaceEnabled,
    thirdPlaceRule: form.tournamentType !== 2 && form.thirdPlaceEnabled
      ? serializeRule({
        ...thirdPlaceRule,
        bestOf: isRelay.value ? 1 : thirdPlaceRule.bestOf,
        gamesToWin: isRelay.value ? 1 : thirdPlaceRule.gamesToWin,
        enableDeuce: isRelay.value ? false : thirdPlaceRule.enableDeuce,
      })
      : undefined,
    rule: serializeRule({
      ...form.rule,
      bestOf: isRelay.value ? 1 : form.rule.bestOf,
      gamesToWin: isRelay.value ? 1 : form.rule.gamesToWin,
      enableDeuce: isRelay.value ? false : form.rule.enableDeuce,
    }),
    roundRuleEnabled: form.roundRuleEnabled && supportsRoundRules.value,
    roundRules: form.roundRuleEnabled && supportsRoundRules.value
      ? form.roundRules.map((item) => ({
        stageType: item.stageType,
        roundNum: item.roundNum,
        rule: serializeRule(item.rule),
      }))
      : undefined,
  }

  if (isIndividual.value) {
    return {
      ...base,
      participantType: 0,
      teamMatchTemplate: 0,
      players: players.filter((player) => player.name).map((player) => ({
        name: player.name,
        seed: player.seed || undefined,
      })),
    }
  }

  return {
    ...base,
    participantType: 1,
    teamMatchTemplate: isBadmintonTeam.value ? form.teamMatchTemplate : 0,
    teams: teams.map((team) => {
      const validMembers = team.members.filter((member) => member.name)
      const savedCaptainIndex = validMembers.findIndex((member) => member.captain)
      const captainIndex = savedCaptainIndex >= 0 ? savedCaptainIndex : 0
      return {
        name: team.name,
        seed: team.seed === null || team.seed === undefined || team.seed === '' ? undefined : Number(team.seed),
        members: validMembers.map((member, memberIndex) => ({
          name: member.name,
          jerseyNumber: isVolleyball.value ? Number(member.jerseyNumber) : undefined,
          captain: memberIndex === captainIndex,
        })),
      }
    }),
  }
}

async function submit() {
  modalError.value = ''
  success.value = ''
  const validationError = validate()
  if (validationError) {
    modalError.value = validationError
    return
  }
  if (!divisionMode.value && form.roundRuleEnabled && supportsRoundRules.value) {
    updateFlattenedRoundRules()
  }
  syncRankingTemplate()
  syncThirdPlaceAvailability()
  submitting.value = true
  try {
    const res = await createTournament(buildPayload())
    success.value = `创建成功：${res.tournamentId}`
    setTimeout(() => router.push('/lobby'), 700)
  } catch (err) {
    modalError.value = err?.message || '创建失败'
  } finally {
    submitting.value = false
  }
}

applyPlayersPaste()
watch(
  () => [form.tournamentType, form.knockoutRounds, form.knockoutSlots, form.teamMatchTemplate, form.roundRuleEnabled, participantCount.value],
  () => {
    syncRankingTemplate()
    syncThirdPlaceAvailability()
    syncRoundRules()
  },
)
// 手写签表：名单变化时同步签位容量；赛制/项目/多组别切换后自动关闭，避免残留无效签位
watch(
  () => [manualDrawEnabled.value, manualRoster.value.map((item) => item.key).join(',')],
  () => {
    if (manualDrawEnabled.value) syncManualSlots()
  },
)
watch(
  () => [form.tournamentType, form.sportType, form.participantType, divisionMode.value],
  () => {
    if (form.tournamentType !== 0 || divisionMode.value) {
      manualDrawEnabled.value = false
      manualSlots.value = []
    }
  },
)
// 手写分组：名单/组数变化时同步分组数组；赛制/项目/多组别切换后自动关闭，避免残留无效分组
watch(
  () => [manualGroupsEnabled.value, manualRoster.value.map((item) => item.key).join(','), manualGroupCount.value],
  () => {
    if (manualGroupsEnabled.value) syncManualGroups()
  },
)
watch(
  () => [form.tournamentType, form.sportType, form.participantType, divisionMode.value],
  () => {
    if (form.tournamentType !== 1 || divisionMode.value) {
      manualGroupsEnabled.value = false
      manualGroups.value = []
    }
  },
)
onMounted(loadProfile)
</script>
<style scoped>
.profile-incomplete-banner {
  margin: 12px 0;
  padding: 10px 14px;
  border: 1px solid var(--warn-gold);
  background: var(--warn-bg);
  color: var(--warn-ink);
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
}
.division-toggle-field {
  grid-column: 1 / -1;
}
.division-toggle span {
  font-weight: normal;
}
.divisions-panel .division-card {
  border: 1px solid rgba(var(--slate-rgb), 0.12);
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 14px;
  display: grid;
  gap: 10px;
}
.division-card-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.division-card-head .division-index {
  font-weight: 600;
  white-space: nowrap;
}
.division-card-head .division-name-input {
  flex: 1;
}
.division-rule-grid,
.division-players-label {
  margin-top: 2px;
}
.division-players-label {
  display: grid;
  gap: 6px;
}
.rule-config-panel .manual-draw-toggle-field {
  flex: 1 1 100%;
}
.manual-draw-panel .panel-head {
  margin-bottom: 8px;
}
.manual-draw-panel > p.muted {
  margin-bottom: 10px;
  font-size: 13px;
  line-height: 1.5;
}
.division-players-label textarea {
  min-height: 72px;
}
.divisions-panel > .ghost-action {
  margin-top: 4px;
}
.division-third-place {
  align-self: end;
}
.division-knockout-rule {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed rgba(var(--slate-rgb), 0.12);
}

.division-knockout-rule-title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: rgba(var(--slate-rgb), 0.72);
}

.division-ranking-panel {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed rgba(var(--slate-rgb), 0.12);
}
.manual-panel-flash {
  animation: manual-panel-flash 1.8s ease;
}

@keyframes manual-panel-flash {
  0%, 55% {
    box-shadow: 0 0 0 3px rgba(var(--focus-rgb), 0.45);
  }
  100% {
    box-shadow: none;
  }
}

.rule-config-panel .manual-groups-toggle-field {
  flex: 1 1 100%;
}
.manual-groups-panel .panel-head {
  margin-bottom: 8px;
}
.manual-groups-panel > p.muted {
  margin-bottom: 10px;
  font-size: 13px;
  line-height: 1.5;
}

</style>
