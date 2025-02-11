import 'Frontend/generated/jar-resources/copilot/copilot.js';
// @ts-ignore
if (import.meta.hot) {
  // @ts-ignore
  import.meta.hot.on('vite:afterUpdate', () => {
    (window as any).Vaadin.copilotPlugins._internals.copilotEventBus.emit('vite-after-update',{});
  });
}

import '@vaadin/vertical-layout/theme/lumo/vaadin-vertical-layout.js';
import '@vaadin/context-menu/theme/lumo/vaadin-context-menu.js';
import '@vaadin/text-field/theme/lumo/vaadin-text-field.js';
import '@vaadin/menu-bar/theme/lumo/vaadin-menu-bar.js';
import '@vaadin/grid/theme/lumo/vaadin-grid.js';
import '@vaadin/grid/theme/lumo/vaadin-grid-tree-column.js';
import '@vaadin/details/theme/lumo/vaadin-details.js';
import '@vaadin/icons/vaadin-iconset.js';
import '@vaadin/icon/vaadin-icon.js';
import './vaadin-featureflags.js';

import './index';

import 'Frontend/generated/jar-resources/vaadin-dev-tools/vaadin-dev-tools.js';

import './theme-envelopify.global.generated.js';
import { applyTheme } from './theme.js';
applyTheme(document);
