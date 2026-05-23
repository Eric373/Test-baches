-- 1. Creación de la Base de Datos
IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = 'BacheoPro_DB')
BEGIN
    CREATE DATABASE BacheoPro_DB;
END
GO

USE BacheoPro_DB;
GO

-- 2. Tablas de Catálogo (Normalización)
CREATE TABLE Cat_Roles (
    id_rol INT IDENTITY(1,1) PRIMARY KEY,
    nombre_rol VARCHAR(20) NOT NULL -- 'Ciudadano', 'Administrador', 'Cuadrilla'
);

CREATE TABLE Cat_Estados (
    id_estado INT IDENTITY(1,1) PRIMARY KEY,
    nombre_estado VARCHAR(30) NOT NULL -- 'Reportado', 'Validado', 'En Reparación', 'Reparado'
);

CREATE TABLE Cat_Categorias (
    id_categoria INT IDENTITY(1,1) PRIMARY KEY,
    nombre_cat VARCHAR(50) NOT NULL
);

-- 3. Tabla de Usuarios con Seguridad
CREATE TABLE Usuarios (
    id_usuario INT IDENTITY(1,1) PRIMARY KEY,
    nombre_completo VARCHAR(100) NOT NULL,
    correo VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARBINARY(MAX) NOT NULL, -- Almacenamiento seguro de contraseñas
    id_rol INT FOREIGN KEY REFERENCES Cat_Roles(id_rol),
    fecha_registro DATETIME2 DEFAULT GETDATE()
);

-- 4. Tabla de Reportes con Geolocalización Real
CREATE TABLE Reportes (
    id_reporte INT IDENTITY(1,1) PRIMARY KEY,
    id_usuario INT FOREIGN KEY REFERENCES Usuarios(id_usuario),
    id_categoria INT FOREIGN KEY REFERENCES Cat_Categorias(id_categoria),
    descripcion TEXT NOT NULL,
    -- Usamos GEOGRAPHY para permitir cálculos de distancia y mapas reales
    ubicacion_geo GEOGRAPHY NOT NULL, 
    direccion_texto VARCHAR(255),
    url_imagen VARCHAR(MAX),
    id_estado INT FOREIGN KEY REFERENCES Cat_Estados(id_estado) DEFAULT 1,
    prioridad TINYINT DEFAULT 1, -- 1: Baja, 2: Media, 3: Alta (IA puede calcular esto)
    fecha_creacion DATETIME2 DEFAULT GETDATE()
);

-- 5. Tabla de Auditoría (Fundamental para Seguridad y Profesionalismo)
-- Registra quién cambió qué y cuándo.
CREATE TABLE Auditoria_Reportes (
    id_log INT IDENTITY(1,1) PRIMARY KEY,
    id_reporte INT,
    id_usuario_cambio INT,
    estado_anterior INT,
    estado_nuevo INT,
    comentario_tecnico TEXT,
    fecha_cambio DATETIME2 DEFAULT GETDATE()
);
GO

-- 6. Insertar Datos Maestros
INSERT INTO Cat_Roles (nombre_rol) VALUES ('Ciudadano'), ('Administrador'), ('Cuadrilla');
INSERT INTO Cat_Estados (nombre_estado) VALUES ('Reportado'), ('Validado'), ('Programado'), ('En Curso'), ('Reparado');
INSERT INTO Cat_Categorias (nombre_cat) VALUES ('Bache Profundo'), ('Grietas'), ('Hundimiento'), ('Desprendimiento');
GO

-- Tabla para registrar los equipos de trabajo (Cuadrillas)
CREATE TABLE Cuadrillas (
    id_cuadrilla INT IDENTITY(1,1) PRIMARY KEY,
    nombre_equipo VARCHAR(50) NOT NULL,
    vehiculo_asignado VARCHAR(50), -- Placas o ID del camión
    zona_cobertura VARCHAR(100)
);

-- Tabla para controlar los materiales e inventario que llevan en el camión
CREATE TABLE Inventario_Materiales (
    id_material INT IDENTITY(1,1) PRIMARY KEY,
    nombre_material VARCHAR(100) NOT NULL, -- ej. Asfalto frío, Grava, Sellador
    unidad_medida VARCHAR(20), -- Toneladas, Litros, Bultos
    stock_disponible DECIMAL(10,2) DEFAULT 0
);

-- Tabla de Asignación (Aquí se une el bache con la ruta de la cuadrilla)
CREATE TABLE Asignaciones_Ruta (
    id_asignacion INT IDENTITY(1,1) PRIMARY KEY,
    id_reporte INT FOREIGN KEY REFERENCES Reportes(id_reporte),
    id_cuadrilla INT FOREIGN KEY REFERENCES Cuadrillas(id_cuadrilla),
    fecha_programada DATE NOT NULL,
    orden_ruta INT, -- Para optimizar qué bache tapan primero y cuál después
    material_estimado DECIMAL(10,2) -- Cuánto asfalto van a gastar ahí
);

-- Tabla para registrar el material exacto que gasta la cuadrilla en el bache
CREATE TABLE Consumo_Materiales (
    id_consumo INT IDENTITY(1,1) PRIMARY KEY,
    id_asignacion INT FOREIGN KEY REFERENCES Asignaciones_Ruta(id_asignacion),
    id_material INT FOREIGN KEY REFERENCES Inventario_Materiales(id_material),
    cantidad_utilizada DECIMAL(10,2) NOT NULL,
    comentario_trabajador VARCHAR(255),
    fecha_registro DATETIME2 DEFAULT GETDATE()
);

-- Conectar la auditoría con el reporte modificado
ALTER TABLE Auditoria_Reportes
ADD CONSTRAINT FK_Auditoria_Reporte FOREIGN KEY (id_reporte) REFERENCES Reportes(id_reporte);

-- Conectar la auditoría con el usuario que hizo el cambio
ALTER TABLE Auditoria_Reportes
ADD CONSTRAINT FK_Auditoria_Usuario FOREIGN KEY (id_usuario_cambio) REFERENCES Usuarios(id_usuario);

-- Conectar los estados anterior y nuevo
ALTER TABLE Auditoria_Reportes
ADD CONSTRAINT FK_Auditoria_EstadoAnt FOREIGN KEY (estado_anterior) REFERENCES Cat_Estados(id_estado);

ALTER TABLE Auditoria_Reportes
ADD CONSTRAINT FK_Auditoria_EstadoNvo FOREIGN KEY (estado_nuevo) REFERENCES Cat_Estados(id_estado);


USE [BacheoPro_DB];
GO
EXEC sp_changedbowner 'sa';
GO