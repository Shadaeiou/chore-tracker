-- Expand the task template library with non-cleaning categories
-- (errands, vehicle, personal/health, financial, plants, family).
-- Phase 5 polish.

-- Errands (8)
INSERT INTO task_templates VALUES ('tmpl-errands-1', 'Grocery shopping',          'errands', 7,   2);
INSERT INTO task_templates VALUES ('tmpl-errands-2', 'Refill prescriptions',      'errands', 30,  1);
INSERT INTO task_templates VALUES ('tmpl-errands-3', 'Mail letters/packages',     'errands', 14,  1);
INSERT INTO task_templates VALUES ('tmpl-errands-4', 'Pick up dry cleaning',      'errands', 14,  1);
INSERT INTO task_templates VALUES ('tmpl-errands-5', 'Bank deposit/errand',       'errands', 14,  1);
INSERT INTO task_templates VALUES ('tmpl-errands-6', 'Buy household supplies',    'errands', 30,  2);
INSERT INTO task_templates VALUES ('tmpl-errands-7', 'Costco/bulk run',           'errands', 30,  3);
INSERT INTO task_templates VALUES ('tmpl-errands-8', 'Return online orders',      'errands', 14,  1);

-- Vehicle (8)
INSERT INTO task_templates VALUES ('tmpl-vehicle-1', 'Oil change',                'vehicle', 90,  3);
INSERT INTO task_templates VALUES ('tmpl-vehicle-2', 'Tire rotation',             'vehicle', 180, 3);
INSERT INTO task_templates VALUES ('tmpl-vehicle-3', 'Check tire pressure',       'vehicle', 30,  1);
INSERT INTO task_templates VALUES ('tmpl-vehicle-4', 'Refuel',                    'vehicle', 7,   1);
INSERT INTO task_templates VALUES ('tmpl-vehicle-5', 'Vacuum interior',           'vehicle', 30,  2);
INSERT INTO task_templates VALUES ('tmpl-vehicle-6', 'Renew registration',        'vehicle', 365, 2);
INSERT INTO task_templates VALUES ('tmpl-vehicle-7', 'Replace wiper blades',      'vehicle', 365, 2);
INSERT INTO task_templates VALUES ('tmpl-vehicle-8', 'Inspection / smog check',   'vehicle', 365, 2);

-- Personal / health (10)
INSERT INTO task_templates VALUES ('tmpl-personal-1',  'Workout / gym',           'personal', 2,   2);
INSERT INTO task_templates VALUES ('tmpl-personal-2',  'Take vitamins',           'personal', 1,   1);
INSERT INTO task_templates VALUES ('tmpl-personal-3',  'Annual physical',         'personal', 365, 2);
INSERT INTO task_templates VALUES ('tmpl-personal-4',  'Dentist checkup',         'personal', 180, 2);
INSERT INTO task_templates VALUES ('tmpl-personal-5',  'Eye exam',                'personal', 365, 2);
INSERT INTO task_templates VALUES ('tmpl-personal-6',  'Haircut',                 'personal', 30,  2);
INSERT INTO task_templates VALUES ('tmpl-personal-7',  'Meditate',                'personal', 1,   1);
INSERT INTO task_templates VALUES ('tmpl-personal-8',  'Journal',                 'personal', 1,   1);
INSERT INTO task_templates VALUES ('tmpl-personal-9',  'Read for 30 min',         'personal', 1,   1);
INSERT INTO task_templates VALUES ('tmpl-personal-10', 'Stretch / yoga',          'personal', 2,   1);

-- Financial (8)
INSERT INTO task_templates VALUES ('tmpl-financial-1', 'Review bank accounts',    'financial', 7,   1);
INSERT INTO task_templates VALUES ('tmpl-financial-2', 'Pay credit card',         'financial', 30,  1);
INSERT INTO task_templates VALUES ('tmpl-financial-3', 'Audit subscriptions',     'financial', 90,  2);
INSERT INTO task_templates VALUES ('tmpl-financial-4', 'Update budget',           'financial', 30,  2);
INSERT INTO task_templates VALUES ('tmpl-financial-5', 'Invest / contribute',     'financial', 30,  1);
INSERT INTO task_templates VALUES ('tmpl-financial-6', 'Review insurance',        'financial', 365, 3);
INSERT INTO task_templates VALUES ('tmpl-financial-7', 'Tax prep',                'financial', 365, 5);
INSERT INTO task_templates VALUES ('tmpl-financial-8', 'Review credit report',    'financial', 90,  2);

-- Plants (6)
INSERT INTO task_templates VALUES ('tmpl-plants-1', 'Water indoor plants',        'plants', 4,  1);
INSERT INTO task_templates VALUES ('tmpl-plants-2', 'Fertilize plants',           'plants', 30, 1);
INSERT INTO task_templates VALUES ('tmpl-plants-3', 'Repot as needed',            'plants', 180,3);
INSERT INTO task_templates VALUES ('tmpl-plants-4', 'Prune / trim',               'plants', 60, 2);
INSERT INTO task_templates VALUES ('tmpl-plants-5', 'Wipe leaves',                'plants', 60, 2);
INSERT INTO task_templates VALUES ('tmpl-plants-6', 'Rotate for sunlight',        'plants', 14, 1);

-- Family / relationships (6)
INSERT INTO task_templates VALUES ('tmpl-family-1', 'Call parents',               'family', 14, 1);
INSERT INTO task_templates VALUES ('tmpl-family-2', 'Date night',                 'family', 14, 2);
INSERT INTO task_templates VALUES ('tmpl-family-3', 'Family game night',          'family', 14, 2);
INSERT INTO task_templates VALUES ('tmpl-family-4', 'Check in with friends',      'family', 14, 1);
INSERT INTO task_templates VALUES ('tmpl-family-5', 'Plan next trip / outing',    'family', 60, 3);
INSERT INTO task_templates VALUES ('tmpl-family-6', 'Birthday / anniversary card','family', 30, 1);
