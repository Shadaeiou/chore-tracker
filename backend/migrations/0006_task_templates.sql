-- Task template library used by the onboarding wizard and "+ from library" flow (Phase 5).
CREATE TABLE task_templates (
  id                       TEXT PRIMARY KEY,
  name                     TEXT NOT NULL,
  suggested_area           TEXT NOT NULL,
  suggested_frequency_days INTEGER NOT NULL,
  suggested_effort         INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX idx_task_templates_area ON task_templates(suggested_area);

-- Kitchen (12)
INSERT INTO task_templates VALUES ('tmpl-kitchen-1',  'Wipe down counters',     'kitchen', 1,  1);
INSERT INTO task_templates VALUES ('tmpl-kitchen-2',  'Clean stovetop',         'kitchen', 7,  2);
INSERT INTO task_templates VALUES ('tmpl-kitchen-3',  'Take out trash',         'kitchen', 3,  1);
INSERT INTO task_templates VALUES ('tmpl-kitchen-4',  'Run dishwasher',         'kitchen', 2,  1);
INSERT INTO task_templates VALUES ('tmpl-kitchen-5',  'Empty dishwasher',       'kitchen', 1,  1);
INSERT INTO task_templates VALUES ('tmpl-kitchen-6',  'Clean microwave',        'kitchen', 14, 2);
INSERT INTO task_templates VALUES ('tmpl-kitchen-7',  'Mop kitchen floor',      'kitchen', 7,  3);
INSERT INTO task_templates VALUES ('tmpl-kitchen-8',  'Clean fridge interior',  'kitchen', 30, 4);
INSERT INTO task_templates VALUES ('tmpl-kitchen-9',  'Clean oven',             'kitchen', 90, 5);
INSERT INTO task_templates VALUES ('tmpl-kitchen-10', 'Wipe small appliances',  'kitchen', 14, 2);
INSERT INTO task_templates VALUES ('tmpl-kitchen-11', 'Clean kitchen sink',     'kitchen', 3,  1);
INSERT INTO task_templates VALUES ('tmpl-kitchen-12', 'Organize pantry',        'kitchen', 60, 3);

-- Bathroom (8)
INSERT INTO task_templates VALUES ('tmpl-bathroom-1', 'Clean toilet',           'bathroom', 7,  2);
INSERT INTO task_templates VALUES ('tmpl-bathroom-2', 'Wipe sink and counter',  'bathroom', 3,  1);
INSERT INTO task_templates VALUES ('tmpl-bathroom-3', 'Scrub shower/tub',       'bathroom', 14, 3);
INSERT INTO task_templates VALUES ('tmpl-bathroom-4', 'Mop bathroom floor',     'bathroom', 7,  2);
INSERT INTO task_templates VALUES ('tmpl-bathroom-5', 'Replace towels',         'bathroom', 7,  1);
INSERT INTO task_templates VALUES ('tmpl-bathroom-6', 'Clean mirror',           'bathroom', 7,  1);
INSERT INTO task_templates VALUES ('tmpl-bathroom-7', 'Empty bathroom trash',   'bathroom', 5,  1);
INSERT INTO task_templates VALUES ('tmpl-bathroom-8', 'Restock toilet paper',   'bathroom', 14, 1);

-- Bedroom (6)
INSERT INTO task_templates VALUES ('tmpl-bedroom-1', 'Make bed',                'bedroom', 1,  1);
INSERT INTO task_templates VALUES ('tmpl-bedroom-2', 'Change sheets',           'bedroom', 14, 2);
INSERT INTO task_templates VALUES ('tmpl-bedroom-3', 'Dust surfaces',           'bedroom', 14, 1);
INSERT INTO task_templates VALUES ('tmpl-bedroom-4', 'Vacuum bedroom',          'bedroom', 7,  2);
INSERT INTO task_templates VALUES ('tmpl-bedroom-5', 'Tidy clothes',            'bedroom', 3,  1);
INSERT INTO task_templates VALUES ('tmpl-bedroom-6', 'Empty laundry hamper',    'bedroom', 5,  1);

-- Living room (6)
INSERT INTO task_templates VALUES ('tmpl-living-1', 'Vacuum living room',       'living', 7,  2);
INSERT INTO task_templates VALUES ('tmpl-living-2', 'Dust surfaces',            'living', 14, 1);
INSERT INTO task_templates VALUES ('tmpl-living-3', 'Wipe down couch',          'living', 30, 2);
INSERT INTO task_templates VALUES ('tmpl-living-4', 'Clean TV and screens',     'living', 14, 1);
INSERT INTO task_templates VALUES ('tmpl-living-5', 'Tidy up',                  'living', 1,  1);
INSERT INTO task_templates VALUES ('tmpl-living-6', 'Wash throw blankets',      'living', 60, 2);

-- Laundry (6)
INSERT INTO task_templates VALUES ('tmpl-laundry-1', 'Wash darks',              'laundry', 7,  2);
INSERT INTO task_templates VALUES ('tmpl-laundry-2', 'Wash whites',             'laundry', 7,  2);
INSERT INTO task_templates VALUES ('tmpl-laundry-3', 'Wash bedding',            'laundry', 14, 3);
INSERT INTO task_templates VALUES ('tmpl-laundry-4', 'Wash towels',             'laundry', 7,  2);
INSERT INTO task_templates VALUES ('tmpl-laundry-5', 'Clean dryer lint trap',   'laundry', 1,  1);
INSERT INTO task_templates VALUES ('tmpl-laundry-6', 'Iron clothes',            'laundry', 14, 2);

-- Outdoor (8)
INSERT INTO task_templates VALUES ('tmpl-outdoor-1', 'Mow lawn',                'outdoor', 7,   4);
INSERT INTO task_templates VALUES ('tmpl-outdoor-2', 'Water plants',            'outdoor', 3,   1);
INSERT INTO task_templates VALUES ('tmpl-outdoor-3', 'Sweep porch',             'outdoor', 7,   2);
INSERT INTO task_templates VALUES ('tmpl-outdoor-4', 'Clean grill',             'outdoor', 14,  3);
INSERT INTO task_templates VALUES ('tmpl-outdoor-5', 'Empty outdoor trash',     'outdoor', 3,   1);
INSERT INTO task_templates VALUES ('tmpl-outdoor-6', 'Trim hedges',             'outdoor', 30,  4);
INSERT INTO task_templates VALUES ('tmpl-outdoor-7', 'Wash car',                'outdoor', 14,  3);
INSERT INTO task_templates VALUES ('tmpl-outdoor-8', 'Clean gutters',           'outdoor', 180, 5);

-- General / whole-house (10)
INSERT INTO task_templates VALUES ('tmpl-general-1', 'Take out recycling',      'general', 7,   1);
INSERT INTO task_templates VALUES ('tmpl-general-2', 'Replace HVAC filter',     'general', 60,  2);
INSERT INTO task_templates VALUES ('tmpl-general-3', 'Vacuum vents',            'general', 90,  2);
INSERT INTO task_templates VALUES ('tmpl-general-4', 'Wipe baseboards',         'general', 60,  3);
INSERT INTO task_templates VALUES ('tmpl-general-5', 'Wash interior windows',   'general', 60,  3);
INSERT INTO task_templates VALUES ('tmpl-general-6', 'Wash exterior windows',   'general', 180, 4);
INSERT INTO task_templates VALUES ('tmpl-general-7', 'Dust ceiling fans',       'general', 60,  2);
INSERT INTO task_templates VALUES ('tmpl-general-8', 'Test smoke alarms',       'general', 90,  1);
INSERT INTO task_templates VALUES ('tmpl-general-9', 'Replace lightbulbs',      'general', 90,  1);
INSERT INTO task_templates VALUES ('tmpl-general-10','Deep clean',              'general', 30,  5);

-- Pets (6)
INSERT INTO task_templates VALUES ('tmpl-pets-1', 'Feed pet',                   'pets', 1,   1);
INSERT INTO task_templates VALUES ('tmpl-pets-2', 'Walk dog',                   'pets', 1,   2);
INSERT INTO task_templates VALUES ('tmpl-pets-3', 'Clean litter box',           'pets', 2,   1);
INSERT INTO task_templates VALUES ('tmpl-pets-4', 'Brush pet',                  'pets', 7,   1);
INSERT INTO task_templates VALUES ('tmpl-pets-5', 'Wash pet bed',               'pets', 30,  2);
INSERT INTO task_templates VALUES ('tmpl-pets-6', 'Refill water bowl',          'pets', 1,   1);

-- Kids (6)
INSERT INTO task_templates VALUES ('tmpl-kids-1', 'Tidy toys',                  'kids', 1,   1);
INSERT INTO task_templates VALUES ('tmpl-kids-2', 'Wash kids clothes',          'kids', 7,   2);
INSERT INTO task_templates VALUES ('tmpl-kids-3', 'Clean play area',            'kids', 3,   2);
INSERT INTO task_templates VALUES ('tmpl-kids-4', 'Sanitize toys',              'kids', 14,  2);
INSERT INTO task_templates VALUES ('tmpl-kids-5', 'Pack school bag',            'kids', 1,   1);
INSERT INTO task_templates VALUES ('tmpl-kids-6', 'Empty kids hamper',          'kids', 5,   1);

-- Seasonal (12)
INSERT INTO task_templates VALUES ('tmpl-seasonal-1', 'Rake leaves',                  'seasonal', 14,  4);
INSERT INTO task_templates VALUES ('tmpl-seasonal-2', 'Shovel snow',                  'seasonal', 3,   4);
INSERT INTO task_templates VALUES ('tmpl-seasonal-3', 'Plant garden',                 'seasonal', 90,  4);
INSERT INTO task_templates VALUES ('tmpl-seasonal-4', 'Winterize outdoor faucets',    'seasonal', 365, 2);
INSERT INTO task_templates VALUES ('tmpl-seasonal-5', 'Service AC',                   'seasonal', 180, 3);
INSERT INTO task_templates VALUES ('tmpl-seasonal-6', 'Service furnace',              'seasonal', 180, 3);
INSERT INTO task_templates VALUES ('tmpl-seasonal-7', 'Inspect roof',                 'seasonal', 180, 3);
INSERT INTO task_templates VALUES ('tmpl-seasonal-8', 'Power wash deck',              'seasonal', 180, 4);
INSERT INTO task_templates VALUES ('tmpl-seasonal-9', 'Clean window screens',         'seasonal', 180, 3);
INSERT INTO task_templates VALUES ('tmpl-seasonal-10', 'Stain deck',                  'seasonal', 730, 5);
INSERT INTO task_templates VALUES ('tmpl-seasonal-11', 'Drain water heater',          'seasonal', 180, 3);
INSERT INTO task_templates VALUES ('tmpl-seasonal-12', 'Test sump pump',              'seasonal', 90,  1);
