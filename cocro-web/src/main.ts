import { bootstrapApplication } from '@angular/platform-browser';

import { RootComponent } from './app/root.component';
import { rootConfig } from './app/rootConfig';

bootstrapApplication(RootComponent, rootConfig)
  .catch((err) => console.error(err));
